#!/usr/bin/env python3
"""
Ablation study runner for L4Project POMDP experiments.
Runs multiple configs (lambda, p_c, lookback, NFR thresholds, disaster scenarios),
3 seeds (222, 223, 224), 500 timesteps each; aggregates MEC/RPL stats and produces CSVs and figures.
"""
from __future__ import annotations

import argparse
import os
import re
import subprocess
import sys
from pathlib import Path
from typing import Any

import numpy as np
import pandas as pd

# Default seeds and paths
SEEDS = [222, 223, 224]
NUM_TIMESTEPS = 500  # fixed in SolvePOMDP.java
STAT_COLS = ["mean", "median", "Q1", "Q3", "lower_fence", "upper_fence"]
# Three principle surprise configs: MIS, CC, and no surprise (useSurpriseUpdating=false)
SURPRISE_OPTIONS = ["MIS", "CC", "no_surprise"]


def project_root() -> Path:
    """L4Project directory (parent of scripts/)."""
    root = Path(__file__).resolve().parent.parent
    if not (root / "src" / "solver.config").exists():
        raise FileNotFoundError(f"Project root not found: expected {root}/src/solver.config")
    return root


def load_config_template(root: Path) -> list[str]:
    """Load solver.config as list of lines (preserving structure)."""
    path = root / "src" / "solver.config"
    with open(path, "r", encoding="utf-8") as f:
        return f.readlines()


def set_config_value(lines: list[str], key: str, value: str) -> list[str]:
    """Override or append key=value in config lines. Only non-comment lines are replaced."""
    key_eq = key + "="
    out = []
    found = False
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("#"):
            out.append(line)
            continue
        if stripped.startswith(key_eq) or re.match(r"^" + re.escape(key) + r"\s*=", stripped):
            out.append(f"{key}={value}\n")
            found = True
            continue
        out.append(line)
    if not found:
        out.append(f"{key}={value}\n")
    return out


def remove_or_comment_config(lines: list[str], key: str) -> list[str]:
    """Comment out lines that set key (for optional keys we want to set explicitly)."""
    key_eq = key + "="
    out = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith(key_eq) or (not stripped.startswith("#") and re.match(r"^" + re.escape(key) + r"\s*=", stripped)):
            out.append("# " + line.lstrip())
            continue
        out.append(line)
    return out


def write_run_config(
    root: Path,
    output_dir_relative: str,
    run_seed: int,
    lambda_val: float | None = None,
    p_c: float | None = None,
    lookback: int | None = None,
    mec_threshold: float | None = None,
    rpl_threshold: float | None = None,
    surprise: str = "MIS",
    link_failure_timestep: int | None = None,
    link_failure_links: str | None = None,
    link_recovery_timestep: int | None = None,
) -> Path:
    """Write a per-run config file; returns path to the config file."""
    lines = load_config_template(root)
    lines = set_config_value(lines, "outputDirectory", output_dir_relative.replace("\\", "/"))
    lines = set_config_value(lines, "runSeed", str(run_seed))
    if lambda_val is not None:
        lines = set_config_value(lines, "lambda", str(lambda_val))
    if p_c is not None:
        lines = set_config_value(lines, "p_c", str(p_c))
    if lookback is not None:
        lines = set_config_value(lines, "lookback", str(lookback))
    if mec_threshold is not None:
        lines = set_config_value(lines, "mecThreshold", str(mec_threshold))
    if rpl_threshold is not None:
        lines = set_config_value(lines, "rplThreshold", str(rpl_threshold))
    if surprise and surprise.lower() not in ("none", "no_surprise"):
        lines = set_config_value(lines, "surpriseMeasureForGamma", surprise)
        lines = set_config_value(lines, "useSurpriseUpdating", "true")
    else:
        lines = set_config_value(lines, "surpriseMeasureForGamma", "MIS")
        lines = set_config_value(lines, "useSurpriseUpdating", "false")
    if link_failure_timestep is not None and link_failure_timestep >= 0:
        lines = set_config_value(lines, "linkFailureTimestep", str(link_failure_timestep))
        if link_failure_links:
            lines = set_config_value(lines, "linkFailureLinks", link_failure_links)
        if link_recovery_timestep is not None and link_recovery_timestep >= 0:
            lines = set_config_value(lines, "linkRecoveryTimestep", str(link_recovery_timestep))
    else:
        # Ensure link failure is disabled
        lines = set_config_value(lines, "linkFailureTimestep", "")

    configs_dir = root / "output_dir" / "results" / "configs"
    configs_dir.mkdir(parents=True, exist_ok=True)
    # Unique config filename from params
    name = f"run_s{run_seed}"
    if lambda_val is not None:
        name += f"_lam{lambda_val}"
    if p_c is not None:
        name += f"_pc{p_c}"
    if lookback is not None:
        name += f"_m{lookback}"
    if mec_threshold is not None:
        name += f"_mec{mec_threshold}"
    if rpl_threshold is not None:
        name += f"_rpl{rpl_threshold}"
    if surprise:
        name += f"_{surprise}"
    config_path = configs_dir / f"{name}.config"
    with open(config_path, "w", encoding="utf-8") as f:
        f.writelines(lines)
    return config_path


def run_java_solver(root: Path, config_path: Path, cp_sep: str) -> bool:
    """Run main.SolvePOMDP with -DconfigPath=config_path. Returns True on success."""
    cp = f"bin{cp_sep}libraries/*"
    cmd = [
        "java",
        f"-DconfigPath={config_path.resolve()}",
        "-cp", cp,
        "main.SolvePOMDP",
    ]
    try:
        result = subprocess.run(
            cmd,
            cwd=str(root),
            capture_output=True,
            text=True,
            timeout=3600,
        )
        if result.returncode != 0:
            print(result.stderr or result.stdout, file=sys.stderr)
            return False
        return True
    except subprocess.TimeoutExpired:
        print("Run timed out", file=sys.stderr)
        return False
    except Exception as e:
        print(f"Run failed: {e}", file=sys.stderr)
        return False


def compute_six_stats(series: pd.Series) -> dict[str, float]:
    """Compute mean, median, Q1, Q3, lower_fence, upper_fence."""
    q1 = series.quantile(0.25)
    q3 = series.quantile(0.75)
    iqr = q3 - q1
    lower_fence = q1 - 1.5 * iqr
    upper_fence = q3 + 1.5 * iqr
    return {
        "mean": float(series.mean()),
        "median": float(series.median()),
        "Q1": float(q1),
        "Q3": float(q3),
        "lower_fence": float(lower_fence),
        "upper_fence": float(upper_fence),
    }


def read_timestep_file(run_dir: Path, filename: str) -> pd.Series | None:
    """Read MECSattimestep.txt or RPLSattimestep.txt (two columns: timestep, value). Return value series."""
    path = run_dir / filename
    if not path.exists():
        return None
    try:
        df = pd.read_csv(path, sep=r"\s+", header=None, on_bad_lines="skip", engine="python")
        if df.shape[1] >= 2 and len(df) > 0:
            return df.iloc[:, 1]
        return None
    except Exception:
        return None


def aggregate_run_stats(run_dir: Path) -> dict[str, Any] | None:
    """Compute six stats for MEC and RPL from a run directory. Returns dict with keys mean_MEC, median_MEC, ... or None if files missing."""
    mec = read_timestep_file(run_dir, "MECSattimestep.txt")
    rpl = read_timestep_file(run_dir, "RPLSattimestep.txt")
    if mec is None or rpl is None:
        return None
    out: dict[str, Any] = {}
    for name, series in [("MEC", mec), ("RPL", rpl)]:
        s = compute_six_stats(series)
        for k, v in s.items():
            out[f"{k}_{name}"] = v
    return out


# --- Ablation definitions (factor levels; fixed baseline as in plan) ---
ABL1_LAMBDA = {
    "id": "abl1_lambda",
    "name": "lambda",
    "levels": [0.1, 0.5, 1.0, 2.0, 5.0],
    "fixed": {"p_c": 0.5, "lookback": 4, "surprise": "MIS", "mec": 20, "rpl": 0.2, "disaster": None},
}
ABL2_PC = {
    "id": "abl2_pc",
    "name": "p_c",
    "levels": [0.25, 0.5, 0.75],
    "fixed": {"lambda": 1.0, "lookback": 4, "surprise": "MIS", "mec": 20, "rpl": 0.2, "disaster": None},
}
ABL3_LOOKBACK = {
    "id": "abl3_lookback",
    "name": "lookback",
    "levels": [2, 3, 4, 5],
    "fixed": {"lambda": 1.0, "p_c": 0.5, "surprise": "MIS", "mec": 20, "rpl": 0.2, "disaster": None},
}
ABL4_NFR = {
    "id": "abl4_nfr",
    "name": "nfr",
    "levels": [(20, 0.2), (17, 0.17), (15, 0.15)],
    "fixed": {"lambda": 1.0, "p_c": 0.5, "lookback": 4, "surprise": "MIS", "disaster": None},
}
ABL5_DISASTER = {
    "id": "abl5_disaster",
    "name": "disaster",
    "levels": [
        ("no_fail", None, None, None),
        ("fail200_12-7", 200, "12-7", -1),
        ("fail100_12-7_12-3", 100, "12-7,12-3", -1),
    ],
    "fixed": {"lambda": 1.0, "p_c": 0.5, "lookback": 4, "surprise": "MIS", "mec": 20, "rpl": 0.2},
}


def run_id_for_abl1(level: float, seed: int) -> str:
    return f"lam{level}_seed{seed}"


def run_id_for_abl2(level: float, seed: int) -> str:
    return f"pc{level}_seed{seed}"


def run_id_for_abl3(level: int, seed: int) -> str:
    return f"m{level}_seed{seed}"


def run_id_for_abl4(level: tuple[float, float], seed: int) -> str:
    mec, rpl = level
    return f"mec{mec}_rpl{rpl}_seed{seed}"


def run_id_for_abl5(level: tuple, seed: int) -> str:
    scenario_id = level[0]
    return f"{scenario_id}_seed{seed}"


def run_single_ablation(
    root: Path,
    abl: dict,
    seeds: list[int],
    cp_sep: str,
    quick: bool,
) -> list[Path]:
    """Launch all runs for one ablation; return list of run output directories."""
    results_base = root / "output_dir" / "results"
    abl_id = abl["id"]
    run_dirs: list[Path] = []
    levels = abl["levels"]
    if quick:
        levels = levels[:2]
    seed_list = seeds[:1] if quick else seeds
    fixed = abl.get("fixed", {})

    for level in levels:
        for surprise in SURPRISE_OPTIONS:
            for seed in seed_list:
                if abl_id == "abl1_lambda":
                    run_id = run_id_for_abl1(level, seed)
                    output_rel = f"output_dir/results/abl1_lambda/{surprise}/{run_id}"
                    config_path = write_run_config(
                        root, output_rel, seed, lambda_val=level,
                        p_c=fixed.get("p_c"), lookback=fixed.get("lookback"),
                        mec_threshold=fixed.get("mec"), rpl_threshold=fixed.get("rpl"),
                        surprise=surprise,
                    )
                elif abl_id == "abl2_pc":
                    run_id = run_id_for_abl2(level, seed)
                    output_rel = f"output_dir/results/abl2_pc/{surprise}/{run_id}"
                    config_path = write_run_config(
                        root, output_rel, seed, lambda_val=fixed.get("lambda"),
                        p_c=level, lookback=fixed.get("lookback"),
                        mec_threshold=fixed.get("mec"), rpl_threshold=fixed.get("rpl"),
                        surprise=surprise,
                    )
                elif abl_id == "abl3_lookback":
                    run_id = run_id_for_abl3(level, seed)
                    output_rel = f"output_dir/results/abl3_lookback/{surprise}/{run_id}"
                    config_path = write_run_config(
                        root, output_rel, seed, lambda_val=fixed.get("lambda"),
                        p_c=fixed.get("p_c"), lookback=level,
                        mec_threshold=fixed.get("mec"), rpl_threshold=fixed.get("rpl"),
                        surprise=surprise,
                    )
                elif abl_id == "abl4_nfr":
                    mec, rpl = level
                    run_id = run_id_for_abl4(level, seed)
                    output_rel = f"output_dir/results/abl4_nfr/{surprise}/{run_id}"
                    config_path = write_run_config(
                        root, output_rel, seed, lambda_val=fixed.get("lambda"),
                        p_c=fixed.get("p_c"), lookback=fixed.get("lookback"),
                        mec_threshold=mec, rpl_threshold=rpl,
                        surprise=surprise,
                    )
                else:  # abl5_disaster
                    scenario_id, fail_ts, links, rec_ts = level[0], level[1], level[2], level[3]
                    run_id = run_id_for_abl5(level, seed)
                    output_rel = f"output_dir/results/abl5_disaster/{surprise}/{run_id}"
                    config_path = write_run_config(
                        root, output_rel, seed, lambda_val=fixed.get("lambda"),
                        p_c=fixed.get("p_c"), lookback=fixed.get("lookback"),
                        mec_threshold=fixed.get("mec"), rpl_threshold=fixed.get("rpl"),
                        surprise=surprise,
                        link_failure_timestep=fail_ts,
                        link_failure_links=links,
                        link_recovery_timestep=rec_ts if rec_ts and rec_ts >= 0 else None,
                    )
                (results_base / abl_id / surprise / run_id).mkdir(parents=True, exist_ok=True)
                ok = run_java_solver(root, config_path, cp_sep)
                run_dir = root / "output_dir" / "results" / abl_id / surprise / run_id
                if ok:
                    run_dirs.append(run_dir)
                else:
                    print(f"Warning: run failed {abl_id} {surprise} {run_id}", file=sys.stderr)
    return run_dirs


def build_summary_per_ablation(root: Path, abl: dict, seeds: list[int]) -> pd.DataFrame | None:
    """From existing run dirs, compute per-run stats and averaged summary for one ablation."""
    abl_id = abl["id"]
    results_base = root / "output_dir" / "results" / abl_id
    if not results_base.exists():
        return None
    rows = []
    levels = abl["levels"]
    fixed = abl.get("fixed", {})
    for level in levels:
        for surprise in SURPRISE_OPTIONS:
            surprise_dir = results_base / surprise
            if not surprise_dir.exists():
                continue
            for seed in seeds:
                if abl_id == "abl1_lambda":
                    run_id = run_id_for_abl1(level, seed)
                elif abl_id == "abl2_pc":
                    run_id = run_id_for_abl2(level, seed)
                elif abl_id == "abl3_lookback":
                    run_id = run_id_for_abl3(level, seed)
                elif abl_id == "abl4_nfr":
                    run_id = run_id_for_abl4(level, seed)
                else:
                    run_id = run_id_for_abl5(level, seed)
                run_dir = surprise_dir / run_id
                stats = aggregate_run_stats(run_dir)
                if stats is None:
                    continue
                row = {"seed": seed, "level": str(level), "surprise": surprise, **stats}
                rows.append(row)
    if not rows:
        return None
    return pd.DataFrame(rows)


def write_summary_csvs(root: Path, abl: dict, seeds: list[int]) -> None:
    """Write summary_abl*_*.csv and summary_abl*_*_avg.csv for one ablation."""
    df = build_summary_per_ablation(root, abl, seeds)
    if df is None or df.empty:
        return
    abl_id = abl["id"]
    results_dir = root / "output_dir" / "results"
    path_per_run = results_dir / f"summary_{abl_id}.csv"
    df.to_csv(path_per_run, index=False)

    # Averaged over seeds; group by level and surprise
    group_cols = ["level", "surprise"]
    if not all(c in df.columns for c in group_cols):
        return
    avg_df = df.groupby(group_cols, as_index=False).agg("mean")
    avg_path = results_dir / f"summary_{abl_id}_avg.csv"
    avg_df.to_csv(avg_path, index=False)


def main_run(args: argparse.Namespace, root: Path, cp_sep: str) -> None:
    """Execute runs for selected ablations."""
    seeds = SEEDS if not args.quick else [222]
    ablations = [
        ABL1_LAMBDA, ABL2_PC, ABL3_LOOKBACK, ABL4_NFR, ABL5_DISASTER
    ]
    if args.ablations:
        ablations = [a for a in ablations if a["id"] in args.ablations]
    (root / "output_dir" / "results").mkdir(parents=True, exist_ok=True)
    for abl in ablations:
        print(f"Running ablation {abl['id']} ...")
        run_single_ablation(root, abl, seeds, cp_sep, args.quick)
    print("Runs done. Building summary CSVs...")
    for abl in ablations:
        write_summary_csvs(root, abl, seeds)


def main_summary_only(args: argparse.Namespace, root: Path) -> None:
    """Only aggregate existing run dirs and write CSVs (no Java runs)."""
    seeds = SEEDS if not args.quick else [222]
    ablations = [
        ABL1_LAMBDA, ABL2_PC, ABL3_LOOKBACK, ABL4_NFR, ABL5_DISASTER
    ]
    if args.ablations:
        ablations = [a for a in ablations if a["id"] in args.ablations]
    for abl in ablations:
        write_summary_csvs(root, abl, seeds)
    print("Summary CSVs written.")


def main_plots(args: argparse.Namespace, root: Path) -> None:
    """Generate figures from summary_*_avg.csv files."""
    try:
        import matplotlib
        matplotlib.use("Agg")
        import matplotlib.pyplot as plt
    except ImportError:
        print("matplotlib not installed; skipping figures.", file=sys.stderr)
        return
    results_dir = root / "output_dir" / "results"
    figures_dir = results_dir / "figures"
    figures_dir.mkdir(parents=True, exist_ok=True)
    ablations = [
        ("abl1_lambda", "lambda", "Lambda"),
        ("abl2_pc", "level", "p_c"),
        ("abl3_lookback", "level", "Lookback (m)"),
        ("abl4_nfr", "level", "NFR (mec, rpl)"),
        ("abl5_disaster", "level", "Disaster scenario"),
    ]
    for abl_id, xcol, xlabel in ablations:
        avg_path = results_dir / f"summary_{abl_id}_avg.csv"
        if not avg_path.exists():
            continue
        df = pd.read_csv(avg_path)
        if xcol not in df.columns or df.empty:
            continue
        has_surprise = "surprise" in df.columns
        fig, axes = plt.subplots(1, 2, figsize=(12, 5))
        for ax, metric in zip(axes, ["mean_MEC", "mean_RPL"]):
            if metric not in df.columns:
                continue
            if has_surprise:
                # Grouped bars: one group per level, 3 bars per group (MIS, CC, no_surprise)
                levels_uniq = df["level"].unique()
                surprises = df["surprise"].unique()
                n_levels = len(levels_uniq)
                n_surp = len(surprises)
                width = 0.8 / n_surp
                for i, surp in enumerate(surprises):
                    sub = df[df["surprise"] == surp].set_index("level").reindex(levels_uniq)
                    vals = sub[metric].values
                    offset = (i - n_surp / 2 + 0.5) * width
                    ax.bar(np.arange(n_levels) + offset, vals, width, label=surp)
                ax.set_xticks(np.arange(n_levels))
                ax.set_xticklabels([str(x) for x in levels_uniq])
                ax.legend()
            else:
                ax.bar(range(len(df)), df[metric], tick_label=df[xcol].astype(str))
            ax.set_ylabel(metric.replace("_", " "))
            ax.set_xlabel(xlabel)
            # NFR reference: MEC threshold 20, RPL threshold 0.2
            if metric == "mean_MEC":
                ax.axhline(y=20, color="red", linestyle="--", alpha=0.7, label="MEC threshold")
            else:
                ax.axhline(y=0.2, color="red", linestyle="--", alpha=0.7, label="RPL threshold")
        plt.suptitle(f"Ablation {abl_id}")
        plt.tight_layout()
        out = figures_dir / f"{abl_id}_metrics.png"
        plt.savefig(out, dpi=150)
        plt.close()
        print(f"Saved {out}")
    print("Figures saved to output_dir/results/figures/")


def main_readme(args: argparse.Namespace, root: Path) -> None:
    """Write output_dir/results/README.md."""
    results_dir = root / "output_dir" / "results"
    readme_path = results_dir / "README.md"
    content = """# Ablation Study Results

This directory contains outputs from the ablation study (run via `scripts/run_ablation.py`). Each ablation is run under **three surprise configurations**: **MIS**, **CC**, and **no_surprise** (useSurpriseUpdating=false), so all other ablation variations (lambda, p_c, lookback, NFR, disaster) are compared within these three settings.

## Directory layout

Each ablation folder (abl1_lambda, abl2_pc, etc.) contains three subfolders, one per surprise option:
- **MIS/** — Mutual Information Surprise (surpriseMeasureForGamma=MIS, useSurpriseUpdating=true).
- **CC/** — Confidence-Corrected surprise (surpriseMeasureForGamma=CC, useSurpriseUpdating=true).
- **no_surprise/** — No surprise modulation (useSurpriseUpdating=false; classic Bayesian updates).

Within each surprise subfolder, run dirs follow the same naming as before:
- **abl1_lambda/{MIS|CC|no_surprise}/lam{value}_seed{seed}/**
- **abl2_pc/{MIS|CC|no_surprise}/pc{value}_seed{seed}/**
- **abl3_lookback/{MIS|CC|no_surprise}/m{value}_seed{seed}/**
- **abl4_nfr/{MIS|CC|no_surprise}/mec{mec}_rpl{rpl}_seed{seed}/**
- **abl5_disaster/{MIS|CC|no_surprise}/{scenario_id}_seed{seed}/**
- **configs/** — Generated per-run config files used for each experiment.
- **figures/** — Plots of mean MEC and mean RPL vs factor levels, with grouped bars per surprise (e.g. `abl1_lambda_metrics.png`).

Each run subdirectory contains the usual solver outputs: `MECSattimestep.txt`, `RPLSattimestep.txt`, `gamma.txt`, etc.

## Summary CSVs

- **summary_abl1_lambda.csv** — One row per (lambda, seed, surprise): level, seed, surprise, mean_MEC, median_MEC, Q1_MEC, Q3_MEC, lower_fence_MEC, upper_fence_MEC, and same for RPL.
- **summary_abl1_lambda_avg.csv** — Same factors, averaged over seeds 222, 223, 224; one row per (level, surprise).
- Similarly: **summary_abl2_pc**, **summary_abl3_lookback**, **summary_abl4_nfr**, **summary_abl5_disaster** (each with a **surprise** column).

## Metrics

For MEC and RPL (from the 500-timestep series per run): **mean**, **median**, **Q1**, **Q3**, **lower_fence** (Q1 − 1.5×IQR), **upper_fence** (Q3 + 1.5×IQR).

## Reproducibility

- **Timesteps:** 500 per run (fixed in SolvePOMDP.java).
- **Seeds:** 222, 223, 224 (3 runs per configuration, averaged).
- **Config:** Each run used a generated config in `configs/`; base template is `src/solver.config`.

## Usage

From project root (L4Project):  
`python scripts/run_ablation.py run` — run all ablations, then write summaries and figures.  
`python scripts/run_ablation.py run --quick` — one seed, two levels per ablation (faster).  
`python scripts/run_ablation.py run --ablations abl1_lambda abl2_pc` — run only those ablations.  
`python scripts/run_ablation.py summary` — aggregate existing run dirs and write CSVs only.  
`python scripts/run_ablation.py plots` — regenerate figures from existing *_avg.csv.  
`python scripts/run_ablation.py readme` — regenerate this README.
"""
    results_dir.mkdir(parents=True, exist_ok=True)
    with open(readme_path, "w", encoding="utf-8") as f:
        f.write(content)
    print(f"Wrote {readme_path}")


def main() -> None:
    parser = argparse.ArgumentParser(description="Ablation study runner for L4Project")
    parser.add_argument("--ablations", nargs="+", help="Run only these ablation IDs (e.g. abl1_lambda abl2_pc)")
    parser.add_argument("--quick", action="store_true", help="Quick mode: 1 seed, 2 levels per ablation")
    sub = parser.add_subparsers(dest="cmd", help="Command")
    for name in ["run", "summary", "plots", "readme"]:
        sp = sub.add_parser(name)
        sp.add_argument("--ablations", nargs="+", dest="ablations", help="Limit to these ablation IDs")
        sp.add_argument("--quick", action="store_true", dest="quick", help="Quick mode")
    args = parser.parse_args()
    # Copy subparser-only args to namespace if missing (when no subcommand given)
    if getattr(args, "ablations", None) is None:
        args.ablations = None
    if getattr(args, "quick", None) is None:
        args.quick = False

    root = project_root()
    cp_sep = ";" if os.name == "nt" else ":"

    if args.cmd == "run":
        main_run(args, root, cp_sep)
        main_plots(args, root)
        main_readme(args, root)
    elif args.cmd == "summary":
        main_summary_only(args, root)
    elif args.cmd == "plots":
        main_plots(args, root)
    elif args.cmd == "readme":
        main_readme(args, root)
    else:
        # Default: run full pipeline
        main_run(args, root, cp_sep)
        main_plots(args, root)
        main_readme(args, root)


if __name__ == "__main__":
    main()

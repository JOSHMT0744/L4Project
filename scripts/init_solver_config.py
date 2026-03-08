#!/usr/bin/env python3
"""
Interactive CLI to initialise solver.config.
Prompts for algorithm type, lambda, number of runs and run seeds (list), surprise
settings, NFR thresholds, and optional link failure/recovery. Writes src/solver.config
(or --output path). When number of runs > 1, always writes per-seed configs for
run_ablation-style execution.
"""
from __future__ import annotations

import argparse
import os
import sys
from pathlib import Path

from config_utils import load_config_template, set_config_value

ALGORITHM_TYPES = ("erperseus", "perseus", "erpbvi", "faserpbvi")
SURPRISE_MEASURES = ("CC", "BF", "MIS")


def project_root() -> Path:
    """L4Project directory (parent of scripts/)."""
    root = Path(__file__).resolve().parent.parent
    if not (root / "src" / "solver.config").exists():
        raise FileNotFoundError(f"Project root not found: expected {root}/src/solver.config")
    return root


def prompt(prompt_text: str, default: str | None = None) -> str:
    """Prompt and return stripped input; use default if user hits Enter."""
    if default is not None:
        msg = f"{prompt_text} [{default}]: "
    else:
        msg = f"{prompt_text}: "
    value = input(msg).strip()
    return value if value else (default or "")


def prompt_algorithm_type(env_val: str | None) -> str:
    """Prompt for algorithmType until valid."""
    while True:
        if env_val is not None:
            raw = env_val
            env_val = None
        else:
            print("  algorithmType: 1=erperseus, 2=perseus, 3=erpbvi, 4=faserpbvi")
            raw = prompt("algorithmType", "erperseus")
        if raw in ALGORITHM_TYPES:
            return raw
        if raw in ("1", "2", "3", "4"):
            return ALGORITHM_TYPES[int(raw) - 1]
        print("  Invalid. Choose 1-4 or one of: erperseus, perseus, erpbvi, faserpbvi")


def prompt_float(
    name: str,
    default: float,
    min_val: float | None = None,
    max_val: float | None = None,
    env_val: str | None = None,
) -> float:
    """Prompt for a float; re-prompt on invalid input."""
    while True:
        raw = env_val or prompt(name, str(default))
        env_val = None
        try:
            v = float(raw)
            if min_val is not None and v < min_val:
                print(f"  Must be >= {min_val}")
                continue
            if max_val is not None and v > max_val:
                print(f"  Must be <= {max_val}")
                continue
            return v
        except ValueError:
            print("  Enter a number")


def prompt_int(
    name: str,
    default: int,
    min_val: int | None = None,
    env_val: str | None = None,
) -> int:
    """Prompt for an int; re-prompt on invalid input."""
    while True:
        raw = env_val or prompt(name, str(default))
        env_val = None
        try:
            v = int(raw)
            if min_val is not None and v < min_val:
                print(f"  Must be >= {min_val}")
                continue
            return v
        except ValueError:
            print("  Enter an integer")


def prompt_bool(name: str, default: bool, env_val: str | None = None) -> bool:
    """Prompt for true/false."""
    default_str = "true" if default else "false"
    while True:
        raw = (env_val or prompt(name, default_str)).strip().lower()
        env_val = None
        if raw in ("true", "t", "yes", "y", "1"):
            return True
        if raw in ("false", "f", "no", "n", "0", ""):
            return False
        print("  Enter true or false")


def prompt_surprise_measure(env_val: str | None) -> str:
    """Prompt for surpriseMeasureForGamma: CC, BF, or MIS."""
    while True:
        if env_val is not None:
            raw = env_val.strip().upper()
            env_val = None
        else:
            print("  surpriseMeasureForGamma: CC, BF, or MIS")
            raw = prompt("surpriseMeasureForGamma", "MIS").strip().upper()
        if raw in SURPRISE_MEASURES:
            return raw
        print("  Invalid. Choose CC, BF, or MIS")


def prompt_link_failure_timestep(env_val: str | None) -> str:
    """Prompt for linkFailureTimestep; empty = disabled."""
    raw = env_val or prompt(
        "linkFailureTimestep (empty to disable)",
        "",
    )
    if not raw or not raw.strip():
        return ""
    try:
        t = int(raw.strip())
        return str(t) if t >= 0 else ""
    except ValueError:
        return ""


def prompt_link_failure_links(env_val: str | None) -> str:
    """Prompt for comma-separated link IDs (e.g. 12-7,12-3)."""
    return (env_val or prompt("linkFailureLinks (comma-separated, e.g. 12-7,12-3)", "")).strip()


def prompt_link_recovery_timestep(env_val: str | None) -> str:
    """Prompt for linkRecoveryTimestep; empty = no recovery."""
    raw = env_val or prompt("linkRecoveryTimestep (empty for no recovery)", "")
    if not raw or not raw.strip():
        return ""
    try:
        t = int(raw.strip())
        return str(t) if t >= 0 else ""
    except ValueError:
        return ""


def get_env(name: str) -> str | None:
    """Get env var SOLVER_<UPPER_NAME>; return None if unset or empty."""
    key = "SOLVER_" + name.upper().replace(".", "_")
    v = os.environ.get(key, "").strip()
    return v if v else None


def prompt_seeds_list(num_runs: int, env_val: str | None) -> list[int]:
    """Prompt for a comma-separated list of seeds; length must equal num_runs."""
    default = ", ".join(str(222 + i) for i in range(num_runs))
    while True:
        raw = env_val or prompt(
            f"seeds (comma-separated, exactly {num_runs} value(s), e.g. {default})",
            default,
        )
        env_val = None
        try:
            seeds = [int(s.strip()) for s in raw.split(",") if s.strip()]
        except ValueError:
            print("  Enter integers separated by commas")
            continue
        if len(seeds) != num_runs:
            print(f"  Expected {num_runs} seed(s), got {len(seeds)}")
            continue
        return seeds


def collect_params(interactive: bool) -> dict:
    """Collect all parameters via prompts or env (when non-interactive)."""
    params = {}
    if interactive:
        print("\n--- General ---")
        params["algorithmType"] = prompt_algorithm_type(get_env("algorithmType"))
        params["lambda"] = prompt_float("lambda", 1.0, min_val=0.0, env_val=get_env("lambda"))
        print("\n--- Run seeds ---")
        params["numRuns"] = prompt_int("number of runs", 1, min_val=1, env_val=get_env("numRuns"))
        params["seeds"] = prompt_seeds_list(params["numRuns"], get_env("runSeeds"))
        print("\n--- Surprise / gamma ---")
        params["useSurpriseUpdating"] = prompt_bool(
            "useSurpriseUpdating", True, get_env("useSurpriseUpdating")
        )
        params["surpriseMeasureForGamma"] = prompt_surprise_measure(
            get_env("surpriseMeasureForGamma")
        )
        params["p_c"] = prompt_float("p_c", 0.5, min_val=0.0, max_val=1.0, env_val=get_env("p_c"))
        params["lookback"] = prompt_int("lookback", 5, min_val=1, env_val=get_env("lookback"))
        print("\n--- NFR thresholds ---")
        params["mecThreshold"] = prompt_float(
            "mecThreshold", 20.0, env_val=get_env("mecThreshold")
        )
        params["rplThreshold"] = prompt_float(
            "rplThreshold", 0.2, env_val=get_env("rplThreshold")
        )
        print("\n--- Link failure (optional) ---")
        params["linkFailureTimestep"] = prompt_link_failure_timestep(
            get_env("linkFailureTimestep")
        )
        if params["linkFailureTimestep"]:
            params["linkFailureLinks"] = prompt_link_failure_links(
                get_env("linkFailureLinks")
            )
            params["linkRecoveryTimestep"] = prompt_link_recovery_timestep(
                get_env("linkRecoveryTimestep")
            )
        else:
            params["linkFailureLinks"] = ""
            params["linkRecoveryTimestep"] = ""
    else:
        params["algorithmType"] = get_env("algorithmType") or "erperseus"
        if params["algorithmType"] not in ALGORITHM_TYPES:
            params["algorithmType"] = "erperseus"
        params["lambda"] = float(get_env("lambda") or "1.0")
        params["numRuns"] = max(1, int(get_env("numRuns") or "1"))
        raw_seeds = get_env("runSeeds")
        if raw_seeds:
            try:
                params["seeds"] = [int(s.strip()) for s in raw_seeds.split(",") if s.strip()]
            except ValueError:
                params["seeds"] = [222 + i for i in range(params["numRuns"])]
            if len(params["seeds"]) != params["numRuns"]:
                params["seeds"] = [222 + i for i in range(params["numRuns"])]
        else:
            params["seeds"] = [222 + i for i in range(params["numRuns"])]
        params["useSurpriseUpdating"] = (
            (get_env("useSurpriseUpdating") or "true").strip().lower() in ("true", "1", "yes")
        )
        params["surpriseMeasureForGamma"] = (get_env("surpriseMeasureForGamma") or "MIS").strip().upper()
        if params["surpriseMeasureForGamma"] not in SURPRISE_MEASURES:
            params["surpriseMeasureForGamma"] = "MIS"
        params["p_c"] = float(get_env("p_c") or "0.5")
        params["lookback"] = int(get_env("lookback") or "5")
        params["mecThreshold"] = float(get_env("mecThreshold") or "20")
        params["rplThreshold"] = float(get_env("rplThreshold") or "0.2")
        params["linkFailureTimestep"] = (get_env("linkFailureTimestep") or "").strip()
        params["linkFailureLinks"] = (get_env("linkFailureLinks") or "").strip()
        params["linkRecoveryTimestep"] = (get_env("linkRecoveryTimestep") or "").strip()
    return params


def build_config_lines(root: Path, params: dict, run_seed: int) -> list[str]:
    """Build config lines from template and params; run_seed for this run."""
    lines = load_config_template(root)
    lines = set_config_value(lines, "algorithmType", params["algorithmType"])
    lines = set_config_value(lines, "lambda", str(params["lambda"]))
    lines = set_config_value(lines, "runSeed", str(run_seed))
    lines = set_config_value(lines, "useSurpriseUpdating", "true" if params["useSurpriseUpdating"] else "false")
    lines = set_config_value(lines, "surpriseMeasureForGamma", params["surpriseMeasureForGamma"])
    lines = set_config_value(lines, "p_c", str(params["p_c"]))
    lines = set_config_value(lines, "lookback", str(params["lookback"]))
    lines = set_config_value(lines, "mecThreshold", str(params["mecThreshold"]))
    lines = set_config_value(lines, "rplThreshold", str(params["rplThreshold"]))
    if params["linkFailureTimestep"]:
        lines = set_config_value(lines, "linkFailureTimestep", params["linkFailureTimestep"])
        lines = set_config_value(lines, "linkFailureLinks", params["linkFailureLinks"])
        lines = set_config_value(lines, "linkRecoveryTimestep", params["linkRecoveryTimestep"] or "")
    else:
        lines = set_config_value(lines, "linkFailureTimestep", "")
        lines = set_config_value(lines, "linkFailureLinks", "")
        lines = set_config_value(lines, "linkRecoveryTimestep", "")
    return lines


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Interactive CLI to initialise solver.config"
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=None,
        help="Write config to this path instead of src/solver.config",
    )
    parser.add_argument(
        "--non-interactive",
        action="store_true",
        dest="non_interactive",
        help="Read parameters from env (SOLVER_ALGORITHMTYPE, SOLVER_LAMBDA, SOLVER_NUMRUNS, SOLVER_RUNSEEDS, etc.)",
    )
    args = parser.parse_args()

    root = project_root()
    interactive = not args.non_interactive
    params = collect_params(interactive)

    seeds = params["seeds"]
    num_runs = len(seeds)

    # Main config: use first seed
    lines = build_config_lines(root, params, seeds[0])
    out_path = (args.output.resolve() if args.output is not None else root / "src" / "solver.config")
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        f.writelines(lines)
    print(f"\nWrote config to {out_path} (runSeed={seeds[0]})")

    if num_runs > 1:
        print(f"Seeds for {num_runs} runs: {seeds}")
        configs_dir = root / "output_dir" / "results" / "configs"
        configs_dir.mkdir(parents=True, exist_ok=True)
        written = []
        for seed in seeds:
            run_lines = build_config_lines(root, params, seed)
            output_rel = f"output_dir/results/init_runs/s{seed}".replace("\\", "/")
            run_lines = set_config_value(run_lines, "outputDirectory", output_rel)
            cfg_path = configs_dir / f"run_s{seed}.config"
            with open(cfg_path, "w", encoding="utf-8") as f:
                f.writelines(run_lines)
            written.append(cfg_path)
        print(f"Wrote {len(written)} per-run configs to {configs_dir}:")
        for p in written:
            print(f"  {p}")
        print(
            'Run each with: java -DconfigPath=<config> -cp ".;bin;libraries/*" main.SolvePOMDP (from project root; quote -cp in Bash)'
        )

    print("Done.")


if __name__ == "__main__":
    main()
    sys.exit(0)

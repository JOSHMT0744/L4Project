#!/usr/bin/env python3
"""
Shared utilities for reading and writing solver.config.
Used by init_solver_config.py and run_ablation.py.
"""
from __future__ import annotations

import re
from pathlib import Path


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

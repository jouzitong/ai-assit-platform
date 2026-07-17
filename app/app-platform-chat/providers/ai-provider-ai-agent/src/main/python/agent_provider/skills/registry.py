from __future__ import annotations

from pathlib import Path


def built_in_skill_roots(base_dir: Path | None = None) -> list[Path]:
    root = base_dir or Path(__file__).resolve().parent
    ignored = {"__pycache__"}
    return [
        item
        for item in sorted(root.iterdir())
        if item.is_dir() and item.name not in ignored and (item / "SKILL.md").is_file()
    ]

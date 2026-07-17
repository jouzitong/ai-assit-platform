from __future__ import annotations

from typing import Any


def validate_input(value: Any) -> list[str]:
    if value is None:
        return ["input is required"]
    return []

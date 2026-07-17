from __future__ import annotations

from pathlib import PurePosixPath
import hashlib


def checksum_matches(checksum: str, content: bytes) -> bool:
    normalized = checksum.lower().removeprefix("sha256:")
    return normalized == hashlib.sha256(content).hexdigest()


def safe_relative_path(value: str) -> str:
    normalized = str(value or "SKILL.md").replace("\\", "/").strip() or "SKILL.md"
    path = PurePosixPath(normalized)
    if path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise ValueError("Skill resource path must be a normalized relative path")
    return str(path)

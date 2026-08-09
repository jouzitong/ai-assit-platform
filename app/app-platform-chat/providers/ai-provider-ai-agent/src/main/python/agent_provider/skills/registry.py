from __future__ import annotations

from copy import deepcopy
import hashlib
import json
from pathlib import Path
import re
from typing import Any

from agent_provider.skills.validator import checksum_matches, safe_relative_path


_SKILL_CODE_PATTERN = re.compile(r"[a-z0-9]+(?:-[a-z0-9]+)*")


def built_in_skill_roots(base_dir: Path | None = None) -> list[Path]:
    root = base_dir or Path(__file__).resolve().parent
    ignored = {"__pycache__"}
    return [
        item
        for item in sorted(root.iterdir())
        if item.is_dir() and item.name not in ignored and (item / "SKILL.md").is_file()
    ]


def built_in_skill_capabilities(base_dir: Path | None = None) -> list[dict[str, Any]]:
    """Freeze package-local skills into deterministic capability descriptors.

    The returned list is intentionally derived only from packages below ``base_dir``.
    Callers can therefore replace, rather than merge, remotely supplied skill
    capabilities when constructing a PYTHON_LOCAL runtime snapshot.
    """

    package_root = (base_dir or Path(__file__).resolve().parent).resolve()
    capabilities: list[dict[str, Any]] = []
    seen_refs: set[str] = set()

    for discovered_root in built_in_skill_roots(package_root):
        skill_root = discovered_root.resolve()
        _ensure_within_root(skill_root, package_root, "Built-in skill package")
        manifest = _read_manifest(skill_root)
        code = _required_text(manifest, "code", skill_root)
        if not _SKILL_CODE_PATTERN.fullmatch(code):
            raise ValueError(
                f"Built-in skill code must use lowercase kebab-case: {skill_root / 'manifest.json'}"
            )
        if code != skill_root.name:
            raise ValueError(
                f"Built-in skill code must match its directory name: {skill_root / 'manifest.json'}"
            )

        version = manifest.get("version")
        if isinstance(version, bool) or not isinstance(version, int) or version < 1:
            raise ValueError(
                f"Built-in skill version must be a positive integer: {skill_root / 'manifest.json'}"
            )

        name = _required_text(manifest, "name", skill_root)
        description = _required_text(manifest, "description", skill_root)
        normalized_manifest = _freeze_manifest(manifest, skill_root)
        ref = f"skill://{code}/v{version}"
        if ref in seen_refs:
            raise ValueError(f"Duplicate built-in skill reference: {ref}")
        seen_refs.add(ref)

        capabilities.append({
            "key": code,
            "ref": ref,
            "code": code,
            "version": version,
            "name": name,
            "description": description,
            "rootPath": str(skill_root),
            "contentHash": _content_hash(normalized_manifest),
            "manifest": normalized_manifest,
        })

    return capabilities


def _read_manifest(skill_root: Path) -> dict[str, Any]:
    manifest_path = skill_root / "manifest.json"
    if not manifest_path.is_file():
        raise ValueError(f"Built-in skill manifest not found: {manifest_path}")
    try:
        value = json.loads(manifest_path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ValueError(f"Built-in skill manifest is invalid: {manifest_path}") from exc
    if not isinstance(value, dict):
        raise ValueError(f"Built-in skill manifest must be a JSON object: {manifest_path}")
    return value


def _required_text(manifest: dict[str, Any], field: str, skill_root: Path) -> str:
    value = manifest.get(field)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(
            f"Built-in skill manifest field '{field}' must be non-empty: {skill_root / 'manifest.json'}"
        )
    return value.strip()


def _freeze_manifest(manifest: dict[str, Any], skill_root: Path) -> dict[str, Any]:
    source_files = manifest.get("files")
    if not isinstance(source_files, list) or not source_files:
        raise ValueError(
            f"Built-in skill manifest must declare at least one file: {skill_root / 'manifest.json'}"
        )

    frozen_files: list[dict[str, Any]] = []
    seen_paths: set[str] = set()
    for item in source_files:
        if not isinstance(item, dict):
            raise ValueError(
                f"Built-in skill file descriptor must be an object: {skill_root / 'manifest.json'}"
            )
        raw_path = item.get("path")
        if not isinstance(raw_path, str) or not raw_path.strip():
            raise ValueError(
                f"Built-in skill file path must be non-empty: {skill_root / 'manifest.json'}"
            )
        relative_path = safe_relative_path(raw_path)
        if relative_path in seen_paths:
            raise ValueError(
                f"Duplicate built-in skill file path '{relative_path}': {skill_root / 'manifest.json'}"
            )
        seen_paths.add(relative_path)

        resource_path = (skill_root / relative_path).resolve()
        _ensure_within_root(resource_path, skill_root, "Built-in skill resource")
        if not resource_path.is_file():
            raise ValueError(f"Built-in skill resource not found: {resource_path}")
        try:
            content = resource_path.read_bytes()
        except OSError as exc:
            raise ValueError(f"Built-in skill resource cannot be read: {resource_path}") from exc

        checksum = f"sha256:{hashlib.sha256(content).hexdigest()}"
        declared_checksum = item.get("checksum")
        if declared_checksum is not None:
            if not isinstance(declared_checksum, str) or not checksum_matches(declared_checksum, content):
                raise ValueError(f"Built-in skill checksum mismatch: {resource_path}")
        declared_size = item.get("size")
        if declared_size is not None and declared_size != len(content):
            raise ValueError(f"Built-in skill size mismatch: {resource_path}")

        frozen_item = deepcopy(item)
        frozen_item["path"] = relative_path
        frozen_item["checksum"] = checksum
        frozen_item["size"] = len(content)
        frozen_files.append(frozen_item)

    if "SKILL.md" not in seen_paths:
        raise ValueError(
            f"Built-in skill manifest must declare SKILL.md: {skill_root / 'manifest.json'}"
        )

    frozen_manifest = deepcopy(manifest)
    frozen_manifest["files"] = sorted(frozen_files, key=lambda item: item["path"])
    return frozen_manifest


def _content_hash(manifest: dict[str, Any]) -> str:
    canonical = json.dumps(
        manifest,
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    ).encode("utf-8")
    return f"sha256:{hashlib.sha256(canonical).hexdigest()}"


def _ensure_within_root(candidate: Path, root: Path, label: str) -> None:
    try:
        candidate.relative_to(root)
    except ValueError as exc:
        raise ValueError(f"{label} escapes the configured package root: {candidate}") from exc

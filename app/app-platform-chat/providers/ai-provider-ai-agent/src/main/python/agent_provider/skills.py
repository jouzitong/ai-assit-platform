from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath
import re
import base64
import hashlib
import json
import os
from typing import Any, Callable
from urllib import error, parse, request


MAX_RESOURCE_BYTES = 256 * 1024


@dataclass(frozen=True)
class SkillRecord:
    ref: str
    name: str
    description: str
    content_hash: str | None = None
    root_path: Path | None = None
    inline_files: dict[str, str] = field(default_factory=dict)
    code: str | None = None
    version: int | None = None
    package_files: dict[str, dict[str, Any]] = field(default_factory=dict)

    def metadata(self) -> dict[str, Any]:
        return {
            "ref": self.ref,
            "name": self.name,
            "description": self.description,
            "contentHash": self.content_hash,
        }


class SkillCatalog:
    """Validated skill metadata plus an on-demand, path-confined reader."""

    def __init__(
        self,
        records: list[SkillRecord],
        run: dict[str, Any] | None = None,
        snapshot_hash: str | None = None,
    ) -> None:
        self._records = records
        self._run = run or {}
        self._snapshot_hash = snapshot_hash
        self._aliases: dict[str, SkillRecord] = {}
        for record in records:
            aliases = {record.ref, record.name, record.code, _terminal_ref(record.ref)}
            if record.code and record.version is not None:
                aliases.update({
                    f"skill://{record.code}/v{record.version}",
                    f"skill://{record.code}@{record.version}",
                    f"{record.code}@{record.version}",
                    f"skill://{record.code}",
                })
            for alias in aliases:
                if alias:
                    self._aliases[alias] = record

    @classmethod
    def from_capabilities(
        cls,
        capabilities: dict[str, Any] | None,
        run: dict[str, Any] | None = None,
        snapshot_hash: str | None = None,
    ) -> "SkillCatalog":
        source = (capabilities or {}).get("skills")
        records: list[SkillRecord] = []
        for key, item in _iter_records(source):
            manifest = item.get("manifest") if isinstance(item.get("manifest"), dict) else {}
            code = _text(item.get("code"), manifest.get("code"), _terminal_ref(_text(item.get("ref"), key) or ""))
            version = _optional_int(item.get("version", manifest.get("version")))
            ref = _text(item.get("ref"), key)
            if not ref and code:
                ref = f"skill://{code}/v{version}" if version is not None else f"skill://{code}"
            ref = _text(ref, code, item.get("name"))
            if not ref:
                continue
            name = _text(item.get("name"), manifest.get("name"), code, _terminal_ref(ref)) or ref
            description = _text(
                item.get("description"), item.get("summary"), manifest.get("description")
            ) or ""
            root_value = _text(
                item.get("rootPath"), item.get("extractedPath"), item.get("path"),
                manifest.get("rootPath"), manifest.get("extractedPath"), manifest.get("path"),
            )
            root_path = Path(root_value).expanduser().resolve() if root_value else None
            inline_files = _inline_files(item)
            if not inline_files:
                inline_files = _inline_files(manifest)
            records.append(
                SkillRecord(
                    ref=ref,
                    name=name,
                    description=description,
                    content_hash=_text(item.get("contentHash"), item.get("checksum")),
                    root_path=root_path,
                    inline_files=inline_files,
                    code=code,
                    version=version,
                    package_files=_package_files(manifest),
                )
            )
        return cls(records, run, snapshot_hash)

    def metadata_for(self, refs: list[str] | None = None) -> list[dict[str, Any]]:
        if not refs:
            return [record.metadata() for record in self._records]
        resolved: list[dict[str, Any]] = []
        seen: set[str] = set()
        for ref in refs:
            record = self.resolve(ref)
            if record.ref in seen:
                continue
            resolved.append(record.metadata())
            seen.add(record.ref)
        return resolved

    def resolve(self, skill_ref: str) -> SkillRecord:
        record = self._aliases.get(str(skill_ref or "").strip())
        if record is None:
            raise ValueError(f"Unknown skill reference: {skill_ref}")
        return record

    def read(
        self,
        skill_ref: str,
        resource_path: str = "SKILL.md",
        on_loaded: Callable[[SkillRecord, str], None] | None = None,
    ) -> dict[str, Any]:
        record = self.resolve(skill_ref)
        relative = _safe_relative_path(resource_path)
        content = record.inline_files.get(relative)
        gateway_metadata: dict[str, Any] = {}
        if content is None and record.root_path is not None:
            candidate = (record.root_path / relative).resolve()
            try:
                candidate.relative_to(record.root_path)
            except ValueError as exc:
                raise ValueError("Skill resource escapes the configured package root") from exc
            if not candidate.is_file():
                raise ValueError(f"Skill resource not found: {relative}")
            size = candidate.stat().st_size
            if size > MAX_RESOURCE_BYTES:
                raise ValueError(f"Skill resource is larger than {MAX_RESOURCE_BYTES} bytes")
            content = candidate.read_text(encoding="utf-8")
        if content is None:
            content, gateway_metadata = self._read_gateway(record, relative)
        if gateway_metadata.get("encoding") != "base64" and len(content.encode("utf-8")) > MAX_RESOURCE_BYTES:
            raise ValueError(f"Skill resource is larger than {MAX_RESOURCE_BYTES} bytes")
        if on_loaded is not None:
            on_loaded(record, relative)
        return {
            "skillRef": record.ref,
            "skillName": record.name,
            "resourcePath": relative,
            "contentHash": record.content_hash,
            "content": content,
            **gateway_metadata,
        }

    def _read_gateway(self, record: SkillRecord, relative: str) -> tuple[str, dict[str, Any]]:
        if not record.code or record.version is None:
            raise ValueError(f"Skill resource not found: {relative}")
        expected = record.package_files.get(relative)
        if record.package_files and expected is None:
            raise ValueError(f"Skill resource is not part of the frozen package: {relative}")
        base_url = (
            os.getenv("AI_AGENT_SKILL_GATEWAY_URL")
            or os.getenv("AI_AGENT_TOOL_GATEWAY_URL")
            or ""
        ).strip().rstrip("/")
        token = (
            os.getenv("AI_AGENT_SKILL_GATEWAY_TOKEN")
            or os.getenv("AI_AGENT_TOOL_GATEWAY_TOKEN")
            or ""
        ).strip()
        if not base_url:
            raise ValueError("AI_AGENT_SKILL_GATEWAY_URL is required")
        if not token:
            raise ValueError("AI_AGENT_SKILL_GATEWAY_TOKEN is required")
        run_id = _text(self._run.get("runId"))
        if not run_id:
            raise ValueError("Skill Gateway runId is required")
        snapshot_hash = _text(self._snapshot_hash)
        if not snapshot_hash:
            raise ValueError("Skill Gateway snapshotHash is required")
        url = (
            f"{base_url}/api/v1/ai/skill-gateway/{parse.quote(record.code, safe='')}"
            f"/versions/{record.version}/resources/read"
        )
        body = json.dumps({
            "path": relative,
            "run": {
                "runId": run_id,
                "snapshotHash": snapshot_hash,
            },
        }, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        http_request = request.Request(
            url,
            data=body,
            headers={
                "Content-Type": "application/json; charset=utf-8",
                "Authorization": token if token.lower().startswith("bearer ") else f"Bearer {token}",
            },
            method="POST",
        )
        try:
            with request.urlopen(http_request, timeout=20) as response:
                response_text = response.read().decode("utf-8", errors="replace")
        except error.HTTPError as exc:
            raise ValueError(f"Skill Gateway HTTP {exc.code}") from None
        except error.URLError as exc:
            raise ValueError(f"Skill Gateway request failed: {exc.reason}") from None
        try:
            decoded = json.loads(response_text) if response_text else {}
        except json.JSONDecodeError:
            raise ValueError("Skill Gateway returned non-JSON") from None
        if not isinstance(decoded, dict):
            raise ValueError("Skill Gateway response must be a JSON object")
        if decoded.get("skillCode") != record.code or _optional_int(decoded.get("skillVersion")) != record.version:
            raise ValueError("Skill Gateway response identity does not match the frozen Skill")
        if decoded.get("path") != relative:
            raise ValueError("Skill Gateway response path does not match the requested resource")
        encoding = str(decoded.get("encoding") or "utf-8").lower()
        raw_content = decoded.get("content")
        if not isinstance(raw_content, str):
            raise ValueError("Skill Gateway response content must be a string")
        if encoding == "base64":
            try:
                content_bytes = base64.b64decode(raw_content, validate=True)
            except ValueError:
                raise ValueError("Skill Gateway returned invalid base64 content") from None
            content = raw_content
        elif encoding == "utf-8":
            content = raw_content
            content_bytes = raw_content.encode("utf-8")
        else:
            raise ValueError(f"Skill Gateway returned unsupported encoding: {encoding}")
        if len(content_bytes) > MAX_RESOURCE_BYTES:
            raise ValueError(f"Skill resource is larger than {MAX_RESOURCE_BYTES} bytes")
        response_checksum = _text(decoded.get("checksum"))
        expected_checksum = _text(expected.get("checksum")) if expected else None
        if expected_checksum and response_checksum != expected_checksum:
            raise ValueError("Skill Gateway checksum does not match the frozen package manifest")
        if response_checksum and not _checksum_matches(response_checksum, content_bytes):
            raise ValueError("Skill Gateway content checksum is invalid")
        return content, {
            "mediaType": decoded.get("mediaType"),
            "checksum": response_checksum,
            "encoding": encoding,
        }

    def __bool__(self) -> bool:
        return bool(self._records)


def _iter_records(value: Any) -> list[tuple[str | None, dict[str, Any]]]:
    if isinstance(value, list):
        return [(None, item) for item in value if isinstance(item, dict)]
    if isinstance(value, dict):
        return [(str(key), item) for key, item in value.items() if isinstance(item, dict)]
    return []


def _inline_files(item: dict[str, Any]) -> dict[str, str]:
    files: dict[str, str] = {}
    source = item.get("files")
    if isinstance(source, dict):
        for key, value in source.items():
            if not isinstance(value, str):
                continue
            files[_safe_relative_path(str(key))] = value
    skill_md = item.get("skillMd") or item.get("content")
    if isinstance(skill_md, str) and "SKILL.md" not in files:
        files["SKILL.md"] = skill_md
    return files


def _package_files(manifest: dict[str, Any]) -> dict[str, dict[str, Any]]:
    files: dict[str, dict[str, Any]] = {}
    for value in manifest.get("files") or []:
        if not isinstance(value, dict):
            continue
        path = value.get("path")
        if not isinstance(path, str):
            continue
        files[_safe_relative_path(path)] = value
    return files


def _checksum_matches(checksum: str, content: bytes) -> bool:
    normalized = checksum.lower().removeprefix("sha256:")
    return normalized == hashlib.sha256(content).hexdigest()


def _safe_relative_path(value: str) -> str:
    normalized = str(value or "SKILL.md").replace("\\", "/").strip() or "SKILL.md"
    path = PurePosixPath(normalized)
    if path.is_absolute() or any(part in {"", ".", ".."} for part in path.parts):
        raise ValueError("Skill resource path must be a normalized relative path")
    return str(path)


def _terminal_ref(value: str) -> str:
    normalized = str(value or "").rstrip("/")
    path = normalized.split("://", 1)[-1]
    parts = [part for part in path.split("/") if part]
    if not parts:
        return ""
    terminal = parts[-1]
    if re.fullmatch(r"v\d+", terminal, flags=re.IGNORECASE) and len(parts) > 1:
        terminal = parts[-2]
    return terminal.split("@", 1)[0]


def _text(*values: Any) -> str | None:
    for value in values:
        if value is None:
            continue
        text = str(value).strip()
        if text:
            return text
    return None


def _optional_int(value: Any) -> int | None:
    try:
        return int(value) if value is not None else None
    except (TypeError, ValueError):
        return None

#!/usr/bin/env python3
"""Bounded, redacted log inspection for ai-assit-platform development incidents."""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
from collections import deque
from pathlib import Path

SENSITIVE_KEY = (
    r"authorization|proxy-authorization|password|passwd|pwd|secret|token|"
    r"api[_-]?key|access[_-]?key|private[_-]?key|setting_value|auth_json"
)
REDACTIONS = (
    (
        re.compile(rf"""(?i)(["'](?:{SENSITIVE_KEY})["']\s*:\s*["'])[^"']*(["'])"""),
        r"\1<redacted>\2",
    ),
    (
        re.compile(
            rf"(?i)(\b(?:{SENSITIVE_KEY})\b\s*[:=]\s*)"
            r"(?:\"[^\"]*\"|'[^']*'|(?:Bearer\s+)?[^\s,;}}\]]+)"
        ),
        r"\1<redacted>",
    ),
    (re.compile(r"(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+"), "<redacted>"),
    (re.compile(r"\bsk-[A-Za-z0-9_-]{12,}\b"), "<redacted>"),
    (re.compile(r"(?i)(\b[a-z][a-z0-9+.-]*://[^:/\s]+:)[^@\s/]+@"), r"\1<redacted>@"),
    (
        re.compile(r"(?i)([?&](?:password|passwd|pwd|token|api[_-]?key)=)[^&\s]+"),
        r"\1<redacted>",
    ),
)
EXCEPTION_PATTERN = re.compile(
    r"(?i)\b(?:error|exception|caused by|suppressed|failed|failure|timeout|timed out)\b"
)


def find_repo_root(explicit: str | None) -> Path:
    candidates = [Path(explicit).expanduser().resolve()] if explicit else []
    candidates.extend((Path.cwd().resolve(), *Path(__file__).resolve().parents))
    seen: set[Path] = set()
    for candidate in candidates:
        for path in (candidate, *candidate.parents):
            if path in seen:
                continue
            seen.add(path)
            if (path / "AGENTS.md").is_file() and (path / "pom.xml").is_file():
                return path
    raise SystemExit("Repository root not found; pass --repo explicitly.")


def redact(text: str) -> str:
    result = text
    for pattern, replacement in REDACTIONS:
        result = pattern.sub(replacement, result)
    return result


def managed_log(repo: Path, service: str) -> Path:
    controller = repo / ".codex/skills/operate-ai-assit-platform/scripts/projectctl.py"
    if not controller.is_file():
        raise SystemExit(f"Project controller is missing: {controller}")
    completed = subprocess.run(
        [
            sys.executable,
            str(controller),
            "--repo",
            str(repo),
            "status",
            service,
            "--json",
        ],
        capture_output=True,
        text=True,
        check=False,
        timeout=15,
    )
    if completed.returncode != 0:
        message = redact(completed.stderr.strip() or completed.stdout.strip())
        raise SystemExit(f"Unable to resolve managed log: {message}")
    try:
        payload = json.loads(completed.stdout)
        value = payload[0].get("logFile") if payload else None
    except (json.JSONDecodeError, IndexError, AttributeError) as exc:
        raise SystemExit(
            "Project controller returned an invalid status payload."
        ) from exc
    if not value:
        raise SystemExit(
            f"No controller-managed log is recorded for {service}; pass --file with the real log path."
        )
    path = Path(value).expanduser().resolve()
    if not path.is_file():
        raise SystemExit(f"Recorded log does not exist: {path}")
    return path


def tail(path: Path, limit: int) -> list[str]:
    with path.open("r", encoding="utf-8", errors="replace") as handle:
        return list(deque(handle, maxlen=limit))


def matches(line: str, args: argparse.Namespace) -> bool:
    if args.trace_id and args.trace_id not in line:
        return False
    if args.contains and not all(value in line for value in args.contains):
        return False
    if args.level and not re.search(
        rf"(?i)(?<![A-Z]){re.escape(args.level)}(?![A-Z])", line
    ):
        return False
    return not args.errors or bool(EXCEPTION_PATTERN.search(line))


def select(lines: list[str], args: argparse.Namespace) -> tuple[list[str], int]:
    filtered = bool(args.trace_id or args.contains or args.level or args.errors)
    if not filtered:
        return lines[-args.lines :], min(len(lines), args.lines)

    matched = [index for index, line in enumerate(lines) if matches(line, args)]
    selected: set[int] = set()
    for index in matched:
        start = max(0, index - args.context)
        end = min(len(lines), index + args.context + 1)
        selected.update(range(start, end))
    ordered = [lines[index] for index in sorted(selected)]
    return ordered[-args.lines :], len(matched)


def render(path: Path, scanned: list[str], args: argparse.Namespace) -> int:
    selected, match_count = select(scanned, args)
    safe_lines = [redact(line.rstrip("\n")) for line in selected]
    if args.json:
        print(
            json.dumps(
                {
                    "source": str(path),
                    "scannedLines": len(scanned),
                    "matchedLines": match_count,
                    "returnedLines": len(safe_lines),
                    "lines": safe_lines,
                },
                ensure_ascii=False,
                indent=2,
            )
        )
    else:
        print(
            f"source={path} scanned={len(scanned)} matched={match_count} "
            f"returned={len(safe_lines)} redaction=enabled"
        )
        for line in safe_lines:
            print(line)
    return 0


def follow(path: Path, args: argparse.Namespace) -> int:
    render(path, tail(path, args.scan_lines), args)
    try:
        with path.open("r", encoding="utf-8", errors="replace") as handle:
            handle.seek(0, 2)
            while True:
                line = handle.readline()
                if line:
                    if matches(line, args):
                        print(redact(line.rstrip("\n")), flush=True)
                    continue
                time.sleep(0.25)
    except KeyboardInterrupt:
        return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", help="Repository root; auto-detected by default")
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument(
        "--service",
        choices=("gateway", "user", "chat", "db-engine", "render", "file", "ui"),
        help="Resolve the log recorded by the project controller",
    )
    source.add_argument(
        "--file", help="Explicit log file for an externally managed process"
    )
    parser.add_argument("--trace-id", help="Require this exact trace ID")
    parser.add_argument(
        "--contains",
        action="append",
        help="Require a literal value; repeat for AND matching",
    )
    parser.add_argument("--level", choices=("TRACE", "DEBUG", "INFO", "WARN", "ERROR"))
    parser.add_argument(
        "--errors", action="store_true", help="Match common exception/failure markers"
    )
    parser.add_argument(
        "--context", type=int, default=2, help="Context lines around each match (0-20)"
    )
    parser.add_argument(
        "--scan-lines", type=int, default=5000, help="Maximum recent lines to scan"
    )
    parser.add_argument(
        "--lines", type=int, default=200, help="Maximum lines to return"
    )
    parser.add_argument(
        "--follow",
        action="store_true",
        help="Continue printing matching redacted lines",
    )
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    if not 0 <= args.context <= 20:
        parser.error("--context must be between 0 and 20")
    if not 1 <= args.scan_lines <= 100_000:
        parser.error("--scan-lines must be between 1 and 100000")
    if not 1 <= args.lines <= 5_000:
        parser.error("--lines must be between 1 and 5000")
    if args.follow and args.json:
        parser.error("--follow cannot be combined with --json")

    repo = find_repo_root(args.repo)
    path = (
        managed_log(repo, args.service)
        if args.service
        else Path(args.file).expanduser().resolve()
    )
    if not path.is_file():
        raise SystemExit(f"Log file does not exist: {path}")
    if args.follow:
        return follow(path, args)
    return render(path, tail(path, args.scan_lines), args)


if __name__ == "__main__":
    raise SystemExit(main())

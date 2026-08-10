#!/usr/bin/env python3
"""Inspect ai-assit-platform prerequisites and configuration drift without mutation."""

from __future__ import annotations

import argparse
import json
import os
import platform
import re
import shutil
import socket
import subprocess
import sys
import xml.etree.ElementTree as ET
from dataclasses import asdict, dataclass
from pathlib import Path

from db_safe_query import (
    ConfigurationError,
    find_repo_root,
    mysql_backend_summary,
    resolve_connection,
)
from projectctl import SERVICES, port_open


@dataclass(frozen=True)
class Check:
    name: str
    status: str
    detail: str


def add(checks: list[Check], name: str, status: str, detail: str) -> None:
    checks.append(Check(name, status, detail))


def first_line(command: list[str], timeout: float = 8.0) -> tuple[int, str]:
    try:
        completed = subprocess.run(command, capture_output=True, text=True, timeout=timeout, check=False)
    except (OSError, subprocess.TimeoutExpired) as error:
        return 127, str(error)
    output = (completed.stdout + "\n" + completed.stderr).strip().splitlines()
    return completed.returncode, output[0].strip() if output else "no version output"


def tool_checks(checks: list[Check]) -> None:
    commands = {
        "java": ["java", "-version"],
        "maven": ["mvn", "-version"],
        "node": ["node", "--version"],
        "npm": ["npm", "--version"],
        "python": ["python3", "--version"],
        "uv": ["uv", "--version"],
        "mysql-client": ["mysql", "--version"],
        "docker": ["docker", "--version"],
        "docker-compose": ["docker", "compose", "version"],
        "codegraph": ["codegraph", "--version"],
    }
    required = {"java", "maven", "node", "npm", "python", "codegraph"}
    for name, command in commands.items():
        if not shutil.which(command[0]):
            add(checks, f"tool:{name}", "error" if name in required else "warn", "not found on PATH")
            continue
        return_code, version = first_line(command)
        status = "ok" if return_code == 0 else ("error" if name in required else "warn")
        add(checks, f"tool:{name}", status, version)


def root_modules(repo: Path) -> list[str]:
    root = ET.parse(repo / "pom.xml").getroot()
    namespace = "{http://maven.apache.org/POM/4.0.0}"
    modules = root.find(f"{namespace}modules")
    if modules is None:
        return []
    return [node.text.strip() for node in modules.findall(f"{namespace}module") if node.text]


def repository_checks(repo: Path, checks: list[Check]) -> None:
    add(checks, "repo:root", "ok", str(repo))
    for relative in ("AGENTS.md", "pom.xml", "docs/dev-spec/README.md", "ai-conversation-ui/package.json"):
        exists = (repo / relative).is_file()
        add(checks, f"file:{relative}", "ok" if exists else "error", "present" if exists else "missing")

    try:
        modules = root_modules(repo)
    except (OSError, ET.ParseError) as error:
        add(checks, "repo:maven-modules", "error", str(error))
        modules = []
    if modules:
        missing = [module for module in modules if not (repo / module).is_dir()]
        status = "error" if missing else "ok"
        detail = ", ".join(modules) if not missing else "missing: " + ", ".join(missing)
        add(checks, "repo:maven-modules", status, detail)

    completed = subprocess.run(["git", "status", "--short"], cwd=repo, capture_output=True, text=True, check=False)
    if completed.returncode == 0:
        count = len([line for line in completed.stdout.splitlines() if line.strip()])
        add(checks, "repo:working-tree", "ok" if count == 0 else "warn", f"{count} changed/untracked path(s)")
    else:
        add(checks, "repo:working-tree", "warn", "git status failed")

    status = subprocess.run(["codegraph", "status"], cwd=repo, capture_output=True, text=True, check=False)
    detail = "index available" if status.returncode == 0 else "status failed; initialize or repair the index"
    add(checks, "repo:codegraph", "ok" if status.returncode == 0 else "error", detail)


def service_manager_checks(repo: Path, checks: list[Check]) -> None:
    config_path = repo / "tools/service-manager/services.json"
    if not config_path.is_file():
        add(checks, "service-manager:config", "warn", "missing")
        return
    try:
        payload = json.loads(config_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        add(checks, "service-manager:config", "error", str(error))
        return
    serialized = json.dumps(payload, ensure_ascii=False)
    if "app-platform-ai-engine" in serialized or "start-ai-engine-local.sh" in serialized:
        add(checks, "service-manager:removed-ai-engine", "warn", "legacy standalone AI Engine entry remains")
    else:
        add(checks, "service-manager:removed-ai-engine", "ok", "no legacy entry")
    if "ai-assit-ui" in serialized or "ai-assit-ui" in (repo / "tools/service-manager/scripts/start-ui-local.sh").read_text(encoding="utf-8"):
        add(checks, "service-manager:frontend-path", "warn", "uses legacy ai-assit-ui; current path is ai-conversation-ui")
    else:
        add(checks, "service-manager:frontend-path", "ok", "uses current frontend path")
    service_names = [str(item.get("name", "")) for item in payload if isinstance(item, dict)] if isinstance(payload, list) else []
    if "app-platform-file" not in serialized and not any("文件" in name or name.lower() == "file" for name in service_names):
        add(checks, "service-manager:file-service", "warn", "File service is absent")
    else:
        add(checks, "service-manager:file-service", "ok", "File service present")

    nacos_script = repo / "tools/service-manager/scripts/start-nacos-local.sh"
    if nacos_script.is_file():
        text = nacos_script.read_text(encoding="utf-8")
        match = re.search(r'^NACOS_ROOT="([^"]+)"', text, re.MULTILINE)
        if match:
            path = Path(match.group(1)).expanduser()
            exists = (path / "start-standalone.sh").is_file()
            add(
                checks,
                "local:nacos-path",
                "warn" if exists else "error",
                f"workstation-specific path {'exists' if exists else 'is missing'}: {path}",
            )
        else:
            add(checks, "local:nacos-path", "warn", "no explicit NACOS_ROOT detected")


def runtime_checks(repo: Path, checks: list[Check], network: bool) -> None:
    for key, service in SERVICES.items():
        status = "open" if port_open(service.port) else "closed"
        add(checks, f"port:{key}", "ok", f"127.0.0.1:{service.port} {status}")
    for key, port in (("mysql-local", 3306), ("redis-local", 6379), ("minio-local", 9000)):
        status = "open" if port_open(port) else "closed"
        add(checks, f"port:{key}", "ok", f"127.0.0.1:{port} {status}")

    try:
        add(checks, "database:client-backend", "ok", mysql_backend_summary())
    except ConfigurationError as error:
        add(checks, "database:client-backend", "error", str(error))

    try:
        connection = resolve_connection(repo, "chat")
        locality = "local" if connection.host in {"127.0.0.1", "localhost", "::1"} else "remote"
        detail = f"{connection.host}:{connection.port}/{connection.database} ({locality}; credential redacted)"
        add(checks, "config:chat-datasource", "ok", detail)
        if network:
            try:
                with socket.create_connection((connection.host, connection.port), timeout=3):
                    pass
                add(checks, "network:chat-datasource", "ok", "TCP connection succeeded")
            except OSError as error:
                add(checks, "network:chat-datasource", "warn", f"TCP connection failed: {error}")
    except ConfigurationError as error:
        add(checks, "config:chat-datasource", "error", str(error))

    base_configured = bool(os.environ.get("RAGFLOW_BASE_URL"))
    key_configured = bool(os.environ.get("RAGFLOW_API_KEY"))
    add(
        checks,
        "config:ragflow-environment",
        "ok" if base_configured and key_configured else "warn",
        f"baseUrlConfigured={base_configured}, apiKeyConfigured={key_configured}; project DB fallback is available",
    )
    ragflow_home = os.environ.get("RAGFLOW_HOME")
    add(
        checks,
        "config:ragflow-home",
        "ok" if ragflow_home and Path(ragflow_home).expanduser().exists() else "warn",
        "configured" if ragflow_home else "not configured; direct Compose operations unavailable",
    )

    python_bootstrap = shutil.which("python3.11")
    python_version = sys.version_info
    if python_bootstrap:
        add(checks, "agent:python-bootstrap", "ok", python_bootstrap)
    elif python_version >= (3, 11):
        add(checks, "agent:python-bootstrap", "warn", "python3.11 command missing; pass --python python3 to init script")
    else:
        add(checks, "agent:python-bootstrap", "error", "Python 3.11+ is required")


def print_checks(checks: list[Check]) -> None:
    width = max(len(item.name) for item in checks)
    for item in checks:
        print(f"[{item.status.upper():5}] {item.name:<{width}}  {item.detail}")
    counts = {name: sum(item.status == name for item in checks) for name in ("ok", "warn", "error")}
    print(f"Summary: ok={counts['ok']} warn={counts['warn']} error={counts['error']}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", help="Repository root; auto-detected by default")
    parser.add_argument("--json", action="store_true")
    parser.add_argument("--network", action="store_true", help="Also probe the configured datasource TCP endpoint")
    parser.add_argument("--strict", action="store_true", help="Return nonzero for warnings as well as errors")
    args = parser.parse_args()

    checks: list[Check] = []
    try:
        repo = find_repo_root(args.repo)
    except ConfigurationError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    add(checks, "host:platform", "ok", f"{platform.system()} {platform.release()} {platform.machine()}")
    repository_checks(repo, checks)
    tool_checks(checks)
    service_manager_checks(repo, checks)
    runtime_checks(repo, checks, args.network)

    if args.json:
        print(json.dumps([asdict(item) for item in checks], ensure_ascii=False, indent=2))
    else:
        print_checks(checks)
    has_error = any(item.status == "error" for item in checks)
    has_warning = any(item.status == "warn" for item in checks)
    return 1 if has_error or (args.strict and has_warning) else 0


if __name__ == "__main__":
    raise SystemExit(main())

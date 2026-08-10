#!/usr/bin/env python3
"""Conservative local process controller for ai-assit-platform.

The controller records only processes that it starts and refuses to kill an
unknown process merely because it owns a project port.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shlex
import shutil
import signal
import socket
import subprocess
import sys
import time
from collections.abc import Iterable
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Service:
    key: str
    label: str
    port: int
    context_path: str
    start_kind: str
    start_value: str
    cwd: str = "."
    stop_kind: str | None = None
    stop_value: str | None = None


SERVICES: dict[str, Service] = {
    "nacos": Service(
        "nacos",
        "Nacos",
        8848,
        "/nacos",
        "script",
        "tools/service-manager/scripts/start-nacos-local.sh",
        stop_kind="script",
        stop_value="tools/service-manager/scripts/stop-nacos-local.sh",
    ),
    "user": Service(
        "user",
        "User",
        8082,
        "/user",
        "script",
        "tools/service-manager/scripts/start-user-local.sh",
    ),
    "chat": Service(
        "chat",
        "Chat",
        13103,
        "/chat",
        "script",
        "tools/service-manager/scripts/start-ai-chat-local.sh",
    ),
    "db-engine": Service(
        "db-engine",
        "DB Engine",
        14102,
        "/dbEngine",
        "script",
        "tools/service-manager/scripts/start-db-engine-local.sh",
    ),
    "render": Service(
        "render",
        "Render",
        14401,
        "/render",
        "script",
        "tools/service-manager/scripts/start-render-local.sh",
    ),
    "file": Service(
        "file",
        "File",
        14103,
        "/file",
        "file-maven",
        "",
    ),
    "gateway": Service(
        "gateway",
        "Gateway",
        9764,
        "/",
        "script",
        "tools/service-manager/scripts/start-gateway-local.sh",
    ),
    "ui": Service(
        "ui",
        "UI",
        5173,
        "/",
        "ui",
        "",
        cwd="ai-conversation-ui",
    ),
    "manager": Service(
        "manager",
        "Service Manager",
        18080,
        "/",
        "script",
        "tools/service-manager.sh",
    ),
    "open-webui": Service(
        "open-webui",
        "Open WebUI",
        3000,
        "/",
        "script",
        "tools/service-manager/scripts/start-open-webui-phase-a-local.sh",
        stop_kind="compose",
        stop_value="tools/open-webui/compose.yaml",
    ),
}

BACKEND_ORDER = ["nacos", "user", "chat", "db-engine", "render", "file", "gateway"]
CORE_ORDER = [*BACKEND_ORDER, "ui"]
PROXY_VARIABLES = (
    "HTTP_PROXY",
    "HTTPS_PROXY",
    "ALL_PROXY",
    "http_proxy",
    "https_proxy",
    "all_proxy",
    "NO_PROXY",
    "no_proxy",
)


def find_repo_root(explicit: str | None) -> Path:
    candidates: list[Path] = []
    if explicit:
        candidates.append(Path(explicit).expanduser().resolve())
    candidates.append(Path.cwd().resolve())
    candidates.extend(Path(__file__).resolve().parents)
    seen: set[Path] = set()
    for candidate in candidates:
        for path in (candidate, *candidate.parents):
            if path in seen:
                continue
            seen.add(path)
            if (path / "pom.xml").is_file() and (path / "AGENTS.md").is_file():
                return path
    raise SystemExit("Repository root not found; pass --repo explicitly.")


def state_dir(repo: Path) -> Path:
    digest = hashlib.sha256(str(repo).encode("utf-8")).hexdigest()[:12]
    base = Path(os.environ.get("XDG_STATE_HOME", Path.home() / ".local" / "state"))
    path = base / "operate-ai-assit-platform" / digest
    path.mkdir(parents=True, exist_ok=True)
    (path / "logs").mkdir(exist_ok=True)
    return path


def state_path(repo: Path) -> Path:
    return state_dir(repo) / "processes.json"


def load_state(repo: Path) -> dict[str, dict]:
    path = state_path(repo)
    if not path.is_file():
        return {}
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
        return payload if isinstance(payload, dict) else {}
    except (OSError, json.JSONDecodeError):
        return {}


def save_state(repo: Path, state: dict[str, dict]) -> None:
    path = state_path(repo)
    temp = path.with_suffix(".tmp")
    temp.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")
    temp.replace(path)


def pid_alive(pid: int | None) -> bool:
    if not isinstance(pid, int) or pid <= 0:
        return False
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


def port_open(port: int, host: str = "127.0.0.1", timeout: float = 0.25) -> bool:
    try:
        with socket.create_connection((host, port), timeout=timeout):
            return True
    except PermissionError:
        lsof = shutil.which("lsof")
        if not lsof:
            return False
        completed = subprocess.run(
            [lsof, "-nP", f"-iTCP:{port}", "-sTCP:LISTEN"],
            capture_output=True,
            text=True,
            check=False,
        )
        return completed.returncode == 0 and bool(completed.stdout.strip())
    except OSError:
        return False


def wait_for_port(port: int, expected_open: bool, timeout: float) -> bool:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if port_open(port) is expected_open:
            return True
        time.sleep(0.5)
    return port_open(port) is expected_open


def clean_env(clear_proxy: bool = False) -> dict[str, str]:
    env = os.environ.copy()
    if clear_proxy:
        for key in PROXY_VARIABLES:
            env.pop(key, None)
    return env


def command_for(repo: Path, service: Service) -> tuple[list[str], Path, dict[str, str]]:
    cwd = (repo / service.cwd).resolve()
    if service.start_kind == "script":
        return ["bash", str((repo / service.start_value).resolve())], cwd, clean_env()
    if service.start_kind == "ui":
        return ["npm", "run", "dev", "--", "--host", "127.0.0.1", "--port", "5173", "--strictPort"], cwd, clean_env(True)
    if service.start_kind == "file-maven":
        shell_command = (
            "unset HTTP_PROXY HTTPS_PROXY ALL_PROXY http_proxy https_proxy all_proxy NO_PROXY no_proxy "
            "JAVA_TOOL_OPTIONS JDK_JAVA_OPTIONS _JAVA_OPTIONS; "
            "mvn -f app/app-platform-file/pom.xml -pl boot -am -DskipTests install && "
            "exec mvn -f app/app-platform-file/boot/pom.xml spring-boot:run "
            "-Dspring-boot.run.jvmArguments='-Djava.net.useSystemProxies=false "
            "-Dhttp.proxyHost= -Dhttp.proxyPort= -Dhttps.proxyHost= -Dhttps.proxyPort= "
            "-DsocksProxyHost= -DsocksProxyPort='"
        )
        return ["bash", "-lc", shell_command], repo, clean_env(True)
    raise RuntimeError(f"Unsupported start kind: {service.start_kind}")


def stop_command_for(repo: Path, service: Service) -> tuple[list[str], Path] | None:
    if service.stop_kind == "script" and service.stop_value:
        return ["bash", str((repo / service.stop_value).resolve())], repo
    if service.stop_kind == "compose" and service.stop_value:
        return ["docker", "compose", "-f", str((repo / service.stop_value).resolve()), "down"], repo
    return None


def expand_target(target: str, include_optional_for_all: bool = False) -> list[str]:
    if target in SERVICES:
        return [target]
    if target == "backend":
        return list(BACKEND_ORDER)
    if target in {"core", "all"}:
        result = list(CORE_ORDER)
        if target == "all" and include_optional_for_all:
            result.extend(["manager", "open-webui"])
        return result
    raise SystemExit(f"Unknown target: {target}")


def snapshot(repo: Path, keys: Iterable[str]) -> list[dict]:
    state = load_state(repo)
    result: list[dict] = []
    for key in keys:
        service = SERVICES[key]
        item = state.get(key, {})
        pid = item.get("pid")
        result.append(
            {
                "key": key,
                "label": service.label,
                "port": service.port,
                "contextPath": service.context_path,
                "ready": port_open(service.port),
                "managed": bool(item),
                "pid": pid if pid_alive(pid) else None,
                "logFile": item.get("logFile"),
                "startedAt": item.get("startedAt"),
            }
        )
    return result


def print_snapshot(items: list[dict]) -> None:
    print(f"{'SERVICE':<14} {'PORT':>5} {'READY':<7} {'OWNER':<10} PID")
    for item in items:
        owner = "managed" if item["managed"] else ("external" if item["ready"] else "-")
        pid = item["pid"] if item["pid"] is not None else "-"
        print(f"{item['key']:<14} {item['port']:>5} {str(item['ready']).lower():<7} {owner:<10} {pid}")


def tail_text(path: Path, lines: int) -> str:
    if not path.is_file():
        return ""
    content = path.read_text(encoding="utf-8", errors="replace").splitlines()
    return "\n".join(content[-lines:])


def start_one(repo: Path, key: str, timeout: float) -> bool:
    service = SERVICES[key]
    state = load_state(repo)
    if port_open(service.port):
        owner = "managed" if key in state else "external"
        print(f"{key}: already ready on 127.0.0.1:{service.port} ({owner})")
        return True

    existing = state.get(key)
    if existing and pid_alive(existing.get("pid")):
        print(f"{key}: managed process {existing['pid']} is still starting; waiting for port {service.port}")
        return wait_for_port(service.port, True, timeout)

    command, cwd, env = command_for(repo, service)
    missing = [str(path) for path in (cwd,) if not path.exists()]
    if service.start_kind == "script" and not (repo / service.start_value).is_file():
        missing.append(str(repo / service.start_value))
    if missing:
        print(f"{key}: missing required path(s): {', '.join(missing)}", file=sys.stderr)
        return False

    timestamp = time.strftime("%Y%m%d-%H%M%S")
    log_path = state_dir(repo) / "logs" / f"{key}-{timestamp}.log"
    log_handle = log_path.open("a", encoding="utf-8")
    print(f"{key}: starting; log={log_path}")
    process = subprocess.Popen(
        command,
        cwd=cwd,
        env=env,
        stdin=subprocess.DEVNULL,
        stdout=log_handle,
        stderr=subprocess.STDOUT,
        start_new_session=True,
        text=True,
    )
    state[key] = {
        "pid": process.pid,
        "port": service.port,
        "command": shlex.join(command),
        "logFile": str(log_path),
        "startedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "repo": str(repo),
    }
    save_state(repo, state)
    log_handle.close()

    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if port_open(service.port):
            print(f"{key}: ready on 127.0.0.1:{service.port}")
            return True
        return_code = process.poll()
        if return_code not in (None, 0):
            print(f"{key}: start command exited with {return_code}", file=sys.stderr)
            recent = tail_text(log_path, 40)
            if recent:
                print(recent, file=sys.stderr)
            return False
        time.sleep(1.0)

    print(f"{key}: process submitted but port {service.port} was not ready within {timeout:.0f}s", file=sys.stderr)
    recent = tail_text(log_path, 20)
    if recent:
        print(recent, file=sys.stderr)
    return False


def terminate_process_group(pid: int, timeout: float) -> bool:
    if not pid_alive(pid):
        return True
    try:
        process_group = os.getpgid(pid)
        os.killpg(process_group, signal.SIGTERM)
    except ProcessLookupError:
        return True
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if not pid_alive(pid):
            return True
        time.sleep(0.5)
    try:
        os.killpg(os.getpgid(pid), signal.SIGKILL)
    except ProcessLookupError:
        return True
    return not pid_alive(pid)


def stop_one(repo: Path, key: str, timeout: float) -> bool:
    service = SERVICES[key]
    state = load_state(repo)
    item = state.get(key)
    if not item:
        if port_open(service.port):
            print(f"{key}: port {service.port} is owned externally; refusing to stop it", file=sys.stderr)
            return False
        print(f"{key}: not running")
        return True

    explicit_stop = stop_command_for(repo, service)
    ok = True
    if explicit_stop:
        command, cwd = explicit_stop
        print(f"{key}: running managed stop command")
        completed = subprocess.run(
            command,
            cwd=cwd,
            env=clean_env(),
            text=True,
            capture_output=True,
            check=False,
        )
        if completed.stdout.strip():
            print(completed.stdout.strip())
        if completed.returncode != 0:
            ok = False
            if completed.stderr.strip():
                print(completed.stderr.strip(), file=sys.stderr)
    else:
        pid = item.get("pid")
        if isinstance(pid, int):
            print(f"{key}: stopping managed process group {pid}")
            ok = terminate_process_group(pid, timeout)
        else:
            ok = not port_open(service.port)

    if ok and wait_for_port(service.port, False, min(timeout, 20.0)):
        state.pop(key, None)
        save_state(repo, state)
        print(f"{key}: stopped")
        return True
    print(f"{key}: stop did not make port {service.port} unavailable", file=sys.stderr)
    return False


def print_plan(repo: Path, keys: Iterable[str]) -> None:
    for key in keys:
        service = SERVICES[key]
        command, cwd, _ = command_for(repo, service)
        print(f"{key}: cwd={cwd}")
        print(f"  {shlex.join(command)}")


def build(repo: Path, target: str, with_tests: bool) -> int:
    module_map = {
        "gateway": "app/app-gateway",
        "user": "app/app-platform-user",
        "chat": "app/app-platform-chat",
        "db-engine": "app/app-platform-db-engine",
        "render": "app/app-platform-render",
        "file": "app/app-platform-file",
    }
    skip = [] if with_tests else ["-DskipTests"]
    if target == "ui":
        return subprocess.run(
            ["npm", "run", "build"], cwd=repo / "ai-conversation-ui", check=False
        ).returncode
    if target in module_map:
        command = ["mvn", "-pl", module_map[target], "-am", "clean", "compile", *skip]
        return subprocess.run(command, cwd=repo, check=False).returncode
    if target in {"backend", "core", "all"}:
        return_code = subprocess.run(["mvn", "clean", "compile", *skip], cwd=repo, check=False).returncode
        if return_code != 0 or target == "backend":
            return return_code
        return subprocess.run(
            ["npm", "run", "build"], cwd=repo / "ai-conversation-ui", check=False
        ).returncode
    raise SystemExit(f"Target cannot be built: {target}")


def follow_log(path: Path, lines: int) -> int:
    return subprocess.run(["tail", "-n", str(lines), "-f", str(path)], check=False).returncode


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", help="Repository root; auto-detected by default")
    subparsers = parser.add_subparsers(dest="action", required=True)

    list_parser = subparsers.add_parser("list", help="List supported services")
    list_parser.add_argument("--json", action="store_true")

    status_parser = subparsers.add_parser("status", help="Check service ports and controller ownership")
    status_parser.add_argument("target", nargs="?", default="all")
    status_parser.add_argument("--json", action="store_true")

    plan_parser = subparsers.add_parser("plan-start", help="Print exact start commands without executing")
    plan_parser.add_argument("target", nargs="?", default="core")

    start_parser = subparsers.add_parser("start", help="Start one service or an ordered scope")
    start_parser.add_argument("target")
    start_parser.add_argument("--timeout", type=float, default=600.0)
    start_parser.add_argument("--continue-on-error", action="store_true")

    stop_parser = subparsers.add_parser("stop", help="Stop only controller-managed services")
    stop_parser.add_argument("target")
    stop_parser.add_argument("--timeout", type=float, default=15.0)
    stop_parser.add_argument("--continue-on-error", action="store_true")

    logs_parser = subparsers.add_parser("logs", help="Read a managed service log")
    logs_parser.add_argument("service", choices=sorted(SERVICES))
    logs_parser.add_argument("--lines", type=int, default=100)
    logs_parser.add_argument("--follow", action="store_true")

    build_parser = subparsers.add_parser("build", help="Run a scoped build")
    build_parser.add_argument("target")
    build_parser.add_argument("--with-tests", action="store_true")

    args = parser.parse_args()
    repo = find_repo_root(args.repo)

    if args.action == "list":
        items = [
            {
                "key": item.key,
                "label": item.label,
                "port": item.port,
                "contextPath": item.context_path,
            }
            for item in SERVICES.values()
        ]
        if args.json:
            print(json.dumps(items, ensure_ascii=False, indent=2))
        else:
            print_snapshot(snapshot(repo, SERVICES))
        return 0

    if args.action == "status":
        keys = list(SERVICES) if args.target == "all" else expand_target(args.target)
        items = snapshot(repo, keys)
        if args.json:
            print(json.dumps(items, ensure_ascii=False, indent=2))
        else:
            print_snapshot(items)
        return 0

    if args.action == "plan-start":
        print_plan(repo, expand_target(args.target))
        return 0

    if args.action == "start":
        ok = True
        for key in expand_target(args.target):
            current = start_one(repo, key, args.timeout)
            ok = current and ok
            if not current and not args.continue_on_error:
                break
        return 0 if ok else 1

    if args.action == "stop":
        ok = True
        for key in reversed(expand_target(args.target)):
            current = stop_one(repo, key, args.timeout)
            ok = current and ok
            if not current and not args.continue_on_error:
                break
        return 0 if ok else 1

    if args.action == "logs":
        item = load_state(repo).get(args.service)
        if not item or not item.get("logFile"):
            print(f"No managed log recorded for {args.service}", file=sys.stderr)
            return 1
        path = Path(item["logFile"])
        if args.follow:
            return follow_log(path, args.lines)
        print(tail_text(path, max(1, args.lines)))
        return 0

    if args.action == "build":
        return build(repo, args.target, args.with_tests)

    return 2


if __name__ == "__main__":
    raise SystemExit(main())

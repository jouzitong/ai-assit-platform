#!/usr/bin/env python3
"""Attach Arthas to an ai-assit-platform JVM with conservative, bounded commands."""

from __future__ import annotations

import argparse
import glob
import json
import os
import re
import shutil
import socket
import subprocess
import sys
from dataclasses import asdict, dataclass
from pathlib import Path


@dataclass(frozen=True)
class ServiceSpec:
    key: str
    port: int
    markers: tuple[str, ...]


@dataclass(frozen=True)
class ProcessTarget:
    service: str
    pid: int
    source: str
    main: str


SERVICES = {
    "gateway": ServiceSpec(
        "gateway",
        9764,
        ("PlatformGatewayApplication", "app-gateway-boot", "app-gateway/boot"),
    ),
    "user": ServiceSpec(
        "user",
        8082,
        ("PlatformUserApplication", "app-platform-user-boot", "app-platform-user/boot"),
    ),
    "chat": ServiceSpec(
        "chat",
        13103,
        (
            "PlatformConversationApplication",
            "app-platform-chat-boot",
            "app-platform-chat/boot",
        ),
    ),
    "db-engine": ServiceSpec(
        "db-engine",
        14102,
        (
            "PlatformDbEngineApplication",
            "app-platform-db-engine-boot",
            "app-platform-db-engine/boot",
        ),
    ),
    "render": ServiceSpec(
        "render",
        14401,
        (
            "PlatformRenderApplication",
            "app-platform-render-boot",
            "app-platform-render/boot",
        ),
    ),
    "file": ServiceSpec(
        "file",
        14103,
        ("PlatformFileApplication", "app-platform-file-boot", "app-platform-file/boot"),
    ),
}
CLASS_NAME = re.compile(r"^(?:[A-Za-z_$][\w$]*\.)+[A-Za-z_$][\w$]*$")
METHOD_NAME = re.compile(r"^(?:[A-Za-z_$][\w$]*|<init>|<clinit>)$")
SAFE_KEY = re.compile(r"^[A-Za-z0-9_.-]{1,160}$")
SAFE_BEAN = re.compile(r"^[A-Za-z_$][A-Za-z0-9_$.-]{0,159}$")
SENSITIVE_NAME = re.compile(
    r"(?i)(password|passwd|pwd|secret|token|credential|api.?key|access.?key|"
    r"private.?key|auth|cookie|setting.?value)"
)
SENSITIVE_OUTPUT_KEY = (
    r"authorization|password|passwd|pwd|secret|token|api[_-]?key|access[_-]?key|"
    r"private[_-]?key|setting_value|auth_json"
)
SENSITIVE_OUTPUT = (
    (
        re.compile(
            rf"""(?i)(["'](?:{SENSITIVE_OUTPUT_KEY})["']\s*:\s*["'])[^"']*(["'])"""
        ),
        r"\1<redacted>\2",
    ),
    (
        re.compile(
            rf"(?i)(\b(?:{SENSITIVE_OUTPUT_KEY})\b\s*[:=]\s*)"
            r"(?:\"[^\"]*\"|'[^']*'|(?:Bearer\s+)?[^\s,;}}\]]+)"
        ),
        r"\1<redacted>",
    ),
    (re.compile(r"(?i)\bBearer\s+[A-Za-z0-9._~+/=-]+"), "<redacted>"),
    (re.compile(r"\bsk-[A-Za-z0-9_-]{12,}\b"), "<redacted>"),
    (
        re.compile(
            r"(?i)(\b(?:[a-z][a-z0-9+.-]*://|jdbc:[a-z0-9]+://)[^:/\s]+:)"
            r"[^@\s/]+@"
        ),
        r"\1<redacted>@",
    ),
    (
        re.compile(r"(?i)([?&](?:password|passwd|pwd|token|api[_-]?key)=)[^&\s]+"),
        r"\1<redacted>",
    ),
)
DISABLED_COMMANDS = (
    "dump,heapdump,mc,redefine,retransform,options,ognl,sysenv,sysprop,profiler,jfr"
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
    for pattern, replacement in SENSITIVE_OUTPUT:
        result = pattern.sub(replacement, result)
    return result


def run_capture(
    command: list[str], timeout: int = 10
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        capture_output=True,
        text=True,
        check=False,
        timeout=timeout,
    )


def jps_entries() -> dict[int, str]:
    jps = shutil.which("jps")
    if not jps and os.environ.get("JAVA_HOME"):
        candidate = Path(os.environ["JAVA_HOME"]) / "bin/jps"
        jps = str(candidate) if candidate.is_file() else None
    if not jps:
        return {}
    completed = run_capture([jps, "-l"])
    if completed.returncode != 0:
        return {}
    entries: dict[int, str] = {}
    for line in completed.stdout.splitlines():
        parts = line.strip().split(maxsplit=1)
        if not parts or not parts[0].isdigit():
            continue
        main = parts[1] if len(parts) == 2 else "java"
        if main.endswith(("sun.tools.jps.Jps", "jdk.jcmd/sun.tools.jps.Jps")):
            continue
        entries[int(parts[0])] = main
    return entries


def ps_value(pid: int, field: str) -> str:
    completed = run_capture(["ps", "-p", str(pid), "-o", f"{field}="])
    return completed.stdout.strip() if completed.returncode == 0 else ""


def lsof_pids(port: int) -> set[int]:
    lsof = shutil.which("lsof")
    if not lsof:
        return set()
    completed = run_capture([lsof, "-nP", f"-iTCP:{port}", "-sTCP:LISTEN", "-t"])
    return {int(value) for value in completed.stdout.split() if value.isdigit()}


def process_matches_service(pid: int, known_main: str, spec: ServiceSpec) -> bool:
    command = ps_value(pid, "command")
    haystack = f"{known_main} {command}".lower()
    is_java = bool(known_main) or bool(
        re.search(r"(?:^|[/\s])java(?:\s|$)", command.lower())
    )
    return is_java and any(marker.lower() in haystack for marker in spec.markers)


def concise_main(main: str, spec: ServiceSpec) -> str:
    if main and "Launcher" not in main:
        return main
    return spec.markers[0]


def discover_targets() -> list[ProcessTarget]:
    entries = jps_entries()
    targets: list[ProcessTarget] = []
    for service, spec in SERVICES.items():
        found: dict[int, ProcessTarget] = {}
        for pid in lsof_pids(spec.port):
            main = entries.get(pid, "")
            if process_matches_service(pid, main, spec):
                found[pid] = ProcessTarget(
                    service, pid, f"port:{spec.port}+marker", concise_main(main, spec)
                )

        for pid, main in entries.items():
            if pid not in found and process_matches_service(pid, main, spec):
                found[pid] = ProcessTarget(
                    service, pid, "jps-marker", concise_main(main, spec)
                )
        targets.extend(found.values())
    return sorted(targets, key=lambda item: (item.service, item.pid))


def resolve_target(
    service: str, pid: int | None, allow_missing: bool
) -> ProcessTarget | None:
    candidates = [item for item in discover_targets() if item.service == service]
    if pid is not None:
        candidates = [item for item in candidates if item.pid == pid]
        if not candidates:
            raise SystemExit(
                f"PID {pid} is not a discovered {service} JVM; refusing to attach."
            )
    if not candidates and allow_missing:
        return None
    if not candidates:
        raise SystemExit(f"No running project JVM found for {service}.")
    if len(candidates) > 1:
        values = ", ".join(str(item.pid) for item in candidates)
        raise SystemExit(
            f"Multiple {service} JVMs found ({values}); pass --pid with one discovered PID."
        )
    target = candidates[0]
    uid = ps_value(target.pid, "uid")
    if uid and uid.isdigit() and int(uid) != os.getuid():
        raise SystemExit(
            f"Target JVM {target.pid} belongs to another user; run as that owner instead of escalating blindly."
        )
    return target


def version_key(path: str) -> tuple[int, ...]:
    try:
        version = Path(path).parents[1].name
        return tuple(int(piece) for piece in re.findall(r"\d+", version))
    except (IndexError, ValueError):
        return (0,)


def locate_arthas_boot(explicit: str | None) -> Path:
    candidates: list[Path] = []
    if explicit:
        candidates.append(Path(explicit).expanduser())
    if os.environ.get("ARTHAS_BOOT_JAR"):
        candidates.append(Path(os.environ["ARTHAS_BOOT_JAR"]).expanduser())
    discovered = sorted(
        glob.glob(str(Path.home() / ".arthas/lib/*/arthas/arthas-boot.jar")),
        key=version_key,
        reverse=True,
    )
    candidates.extend(Path(value) for value in discovered)
    for candidate in candidates:
        path = candidate.resolve()
        if path.is_file():
            return path
    raise SystemExit(
        "arthas-boot.jar was not found; pass --arthas-boot or set ARTHAS_BOOT_JAR. "
        "Do not download an unverified binary during an incident."
    )


def java_command() -> str:
    if os.environ.get("JAVA_HOME"):
        candidate = Path(os.environ["JAVA_HOME"]) / "bin/java"
        if candidate.is_file():
            return str(candidate)
    java = shutil.which("java")
    if not java:
        raise SystemExit("java was not found on PATH or under JAVA_HOME.")
    return java


def free_loopback_port() -> int:
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as listener:
        listener.bind(("127.0.0.1", 0))
        return int(listener.getsockname()[1])


def validate_class_method(class_name: str, method_name: str) -> None:
    if not CLASS_NAME.fullmatch(class_name):
        raise SystemExit(
            "--class-name must be one exact fully qualified class; wildcards are not allowed."
        )
    if not METHOD_NAME.fullmatch(method_name):
        raise SystemExit(
            "--method-name must be one exact method; wildcards are not allowed."
        )


def validate_common(args: argparse.Namespace) -> None:
    if not 1 <= args.timeout <= 300:
        raise SystemExit("--timeout must be between 1 and 300 seconds.")
    if args.environment is None:
        raise SystemExit(
            "Name the target environment explicitly with --environment before attaching."
        )
    if args.environment == "production" and not args.confirm_production:
        raise SystemExit(
            "Production attach requires --environment production --confirm-production."
        )


def method_observation(command: str, args: argparse.Namespace) -> str:
    validate_class_method(args.class_name, args.method_name)
    if not 1 <= args.count <= 5:
        raise SystemExit("--count must be between 1 and 5.")
    if not 0 <= args.min_cost_ms <= 600_000:
        raise SystemExit("--min-cost-ms must be between 0 and 600000.")
    condition = f" '#cost>{args.min_cost_ms}'" if args.min_cost_ms else ""
    return (
        f"{command} {args.class_name} {args.method_name}{condition} "
        f"-n {args.count} -m 1"
    )


def build_commands(args: argparse.Namespace) -> list[str]:
    if args.action == "overview":
        if not 1 <= args.top <= 20:
            raise SystemExit("--top must be between 1 and 20.")
        return ["version", "dashboard -n 1", f"thread -n {args.top}", "jvm", "memory"]
    if args.action == "thread":
        if not 1 <= args.top <= 50:
            raise SystemExit("--top must be between 1 and 50.")
        return [f"thread -n {args.top}"]
    if args.action == "class-info":
        if not CLASS_NAME.fullmatch(args.class_name):
            raise SystemExit("--class-name must be one exact fully qualified class.")
        return [f"sc -d {args.class_name}"]
    if args.action == "methods":
        validate_class_method(args.class_name, args.method_name)
        return [f"sm -d {args.class_name} {args.method_name}"]
    if args.action in {"trace", "stack"}:
        return [method_observation(args.action, args)]
    if args.action == "watch":
        command = method_observation("watch", args)
        if not 1 <= args.depth <= 3:
            raise SystemExit("--depth must be between 1 and 3.")
        expressions = {
            "cost": "#cost",
            "exception": "{throwExp,#cost}",
            "summary": "{params,returnObj,throwExp,#cost}",
        }
        if args.expression != "cost" and not args.allow_value_output:
            raise SystemExit(
                "Exception/parameter/return observation requires --allow-value-output."
            )
        prefix = f"watch {args.class_name} {args.method_name}"
        suffix = command[len(prefix) :]
        return [f"{prefix} '{expressions[args.expression]}'{suffix} -x {args.depth}"]
    if args.action == "spring-property":
        if not SAFE_KEY.fullmatch(args.property_name):
            raise SystemExit("--property-name contains unsupported characters.")
        if SENSITIVE_NAME.search(args.property_name):
            raise SystemExit(
                "Sensitive Spring properties may not be read through this wrapper."
            )
        expression = (
            f'instances[0].getEnvironment().getProperty("{args.property_name}")'
        )
        return [
            (
                "vmtool --action getInstances "
                "--className org.springframework.context.support.AbstractApplicationContext "
                f"-l 1 --express '{expression}'"
            )
        ]
    if args.action == "spring-bean":
        if not SAFE_BEAN.fullmatch(args.bean_name):
            raise SystemExit("--bean-name contains unsupported characters.")
        operations = {
            "contains": "containsBean",
            "local": "containsLocalBean",
            "definition": "containsBeanDefinition",
            "aliases": "getAliases",
        }
        expression = f'instances[0].{operations[args.mode]}("{args.bean_name}")'
        return [
            (
                "vmtool --action getInstances "
                "--className org.springframework.context.support.AbstractApplicationContext "
                f"-l 1 --express '{expression}'"
            )
        ]
    raise SystemExit(f"Unsupported action: {args.action}")


def cleanup(java: str, boot: Path, port: int) -> None:
    client = boot.with_name("arthas-client.jar")
    if not client.is_file():
        print("cleanup=unavailable (arthas-client.jar missing)", file=sys.stderr)
        return
    try:
        completed = run_capture(
            [
                java,
                "-jar",
                str(client),
                "127.0.0.1",
                str(port),
                "-c",
                "reset;stop",
                "--execution-timeout",
                "5000",
            ],
            timeout=10,
        )
        status = "completed" if completed.returncode == 0 else "failed"
        print(f"cleanup={status}", file=sys.stderr)
    except (OSError, subprocess.SubprocessError):
        print("cleanup=failed", file=sys.stderr)


def execute(
    args: argparse.Namespace, target: ProcessTarget | None, commands: list[str]
) -> int:
    validate_common(args)
    boot = locate_arthas_boot(args.arthas_boot)
    java = java_command()
    batch = "; ".join([*commands, "reset", "stop"])
    target_label = (
        str(target.pid) if target else "<resolve-running-service-at-execution>"
    )
    print(
        f"service={args.service} pid={target_label} environment={args.environment} "
        f"timeout={args.timeout}s arthas={boot}"
    )
    for command in commands:
        print(f"command={command}")
    print("cleanup=reset;stop (automatic)")
    if args.dry_run:
        return 0
    if target is None:
        raise SystemExit("A running target JVM is required unless --dry-run is used.")

    port = free_loopback_port()
    command = [
        java,
        "-jar",
        str(boot),
        "--target-ip",
        "127.0.0.1",
        "--telnet-port",
        str(port),
        "--http-port",
        "-1",
        "--session-timeout",
        str(max(60, args.timeout + 15)),
        "--disabled-commands",
        DISABLED_COMMANDS,
        "-c",
        batch,
        str(target.pid),
    ]
    try:
        completed = subprocess.run(
            command,
            capture_output=True,
            text=True,
            check=False,
            timeout=args.timeout,
        )
    except subprocess.TimeoutExpired as exc:
        if exc.stdout:
            value = (
                exc.stdout.decode(errors="replace")
                if isinstance(exc.stdout, bytes)
                else exc.stdout
            )
            print(redact(value.rstrip()))
        if exc.stderr:
            value = (
                exc.stderr.decode(errors="replace")
                if isinstance(exc.stderr, bytes)
                else exc.stderr
            )
            print(redact(value.rstrip()), file=sys.stderr)
        print(
            f"Arthas command timed out after {args.timeout}s; attempting cleanup.",
            file=sys.stderr,
        )
        cleanup(java, boot, port)
        return 124
    if completed.stdout:
        print(redact(completed.stdout.rstrip()))
    if completed.stderr:
        print(redact(completed.stderr.rstrip()), file=sys.stderr)
    if completed.returncode != 0:
        cleanup(java, boot, port)
    return completed.returncode


def add_target(parser: argparse.ArgumentParser) -> None:
    parser.add_argument("--service", required=True, choices=tuple(SERVICES))
    parser.add_argument(
        "--pid", type=int, help="Choose one PID only when multiple service JVMs exist"
    )


def add_method(parser: argparse.ArgumentParser) -> None:
    add_target(parser)
    parser.add_argument("--class-name", required=True)
    parser.add_argument("--method-name", required=True)


def add_observation(parser: argparse.ArgumentParser) -> None:
    add_method(parser)
    parser.add_argument("--count", type=int, default=3)
    parser.add_argument("--min-cost-ms", type=int, default=0)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", help="Repository root; auto-detected by default")
    parser.add_argument("--arthas-boot", help="Verified local arthas-boot.jar")
    parser.add_argument(
        "--environment",
        choices=("local", "dev", "test", "staging", "production"),
        help="Required for every attach or dry-run action; list does not attach",
    )
    parser.add_argument("--confirm-production", action="store_true")
    parser.add_argument(
        "--timeout", type=int, default=60, help="Whole batch timeout in seconds (1-300)"
    )
    parser.add_argument(
        "--dry-run", action="store_true", help="Validate and print without attaching"
    )
    subparsers = parser.add_subparsers(dest="action", required=True)

    list_parser = subparsers.add_parser(
        "list", help="List only discovered project JVMs"
    )
    list_parser.add_argument("--json", action="store_true")

    overview = subparsers.add_parser(
        "overview", help="One bounded dashboard/thread/JVM/memory snapshot"
    )
    add_target(overview)
    overview.add_argument("--top", type=int, default=5)

    thread = subparsers.add_parser("thread", help="Show the busiest threads once")
    add_target(thread)
    thread.add_argument("--top", type=int, default=5)

    class_info = subparsers.add_parser(
        "class-info", help="Inspect one exact loaded class"
    )
    add_target(class_info)
    class_info.add_argument("--class-name", required=True)

    methods = subparsers.add_parser("methods", help="Inspect one exact loaded method")
    add_method(methods)

    trace = subparsers.add_parser(
        "trace", help="Trace one exact method with bounded enhancement"
    )
    add_observation(trace)

    stack = subparsers.add_parser("stack", help="Capture callers of one exact method")
    add_observation(stack)

    watch = subparsers.add_parser(
        "watch", help="Observe one exact method with bounded output"
    )
    add_observation(watch)
    watch.add_argument(
        "--expression", choices=("cost", "exception", "summary"), default="cost"
    )
    watch.add_argument("--depth", type=int, default=1)
    watch.add_argument("--allow-value-output", action="store_true")

    spring_property = subparsers.add_parser(
        "spring-property", help="Read one non-sensitive Spring property"
    )
    add_target(spring_property)
    spring_property.add_argument("--property-name", required=True)

    spring_bean = subparsers.add_parser(
        "spring-bean", help="Check one Bean without instantiating it"
    )
    add_target(spring_bean)
    spring_bean.add_argument("--bean-name", required=True)
    spring_bean.add_argument(
        "--mode",
        choices=("contains", "local", "definition", "aliases"),
        default="contains",
    )
    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    find_repo_root(args.repo)
    if args.action == "list":
        targets = discover_targets()
        if args.json:
            print(
                json.dumps(
                    [asdict(item) for item in targets], ensure_ascii=False, indent=2
                )
            )
        elif targets:
            print(f"{'SERVICE':<12} {'PID':>7} {'SOURCE':<12} MAIN")
            for item in targets:
                print(f"{item.service:<12} {item.pid:>7} {item.source:<12} {item.main}")
        else:
            print("No running ai-assit-platform JVMs discovered.")
        return 0

    target = resolve_target(args.service, args.pid, allow_missing=args.dry_run)
    return execute(args, target, build_commands(args))


if __name__ == "__main__":
    raise SystemExit(main())

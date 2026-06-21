#!/usr/bin/env python3
import json
import os
import shlex
import signal
import subprocess
import threading
import time
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Dict, List, Optional, Tuple
from urllib.parse import parse_qs, urlparse


BASE_DIR = Path(__file__).resolve().parent
REPO_ROOT = BASE_DIR.parent.parent
CONFIG_PATH = BASE_DIR / "services.json"
HTML_PATH = BASE_DIR / "index.html"
RUNTIME_DIR = BASE_DIR / "runtime"
RUNTIME_DIR.mkdir(exist_ok=True)
STATE_PATH = BASE_DIR / "process-state.json"

PROCESS_LOCK = threading.RLock()
RUNNING_PROCESSES: Dict[str, Dict] = {}


def load_services() -> List[Dict]:
    with CONFIG_PATH.open("r", encoding="utf-8") as file:
        services = json.load(file)

    if not isinstance(services, list):
        raise ValueError("services.json must be a JSON array")

    normalized = []
    for service in services:
        if not isinstance(service, dict):
            raise ValueError("each service entry must be an object")

        name = service.get("name")
        shell = service.get("shell", "zsh")
        commands = service.get("commands", [])
        cwd = service.get("cwd", ".")

        if not name or not isinstance(name, str):
            raise ValueError("service.name is required")
        if not isinstance(commands, list):
            raise ValueError(f"service.commands must be a list: {name}")

        normalized_commands = []
        for command in commands:
            if not isinstance(command, dict):
                raise ValueError(f"command entry must be an object: {name}")
            command_name = command.get("name")
            command_line = command.get("command")
            if not command_name or not command_line:
                raise ValueError(f"command.name and command.command are required: {name}")
            normalized_commands.append(
                {
                    "name": command_name,
                    "command": command_line,
                    "background": bool(command.get("background", False)),
                    "cwd": command.get("cwd", cwd),
                    "statusCommand": command.get("statusCommand"),
                    "stopCommand": command.get("stopCommand"),
                    "logFile": command.get("logFile"),
                }
            )

        normalized.append(
            {
                "name": name,
                "shell": shell,
                "cwd": cwd,
                "commands": normalized_commands,
            }
        )
    return normalized


def resolve_cwd(raw_cwd: str) -> Path:
    path = Path(raw_cwd)
    if not path.is_absolute():
        path = (REPO_ROOT / path).resolve()
    return path


def process_key(service_name: str, command_name: str) -> str:
    return f"{service_name}::{command_name}"


def process_is_running(proc: subprocess.Popen) -> bool:
    return proc.poll() is None


def pid_is_running(pid: int) -> bool:
    try:
        os.kill(pid, 0)
    except ProcessLookupError:
        return False
    except PermissionError:
        return True
    return True


def load_process_state() -> Dict[str, Dict]:
    if not STATE_PATH.is_file():
        return {}
    try:
        with STATE_PATH.open("r", encoding="utf-8") as file:
            payload = json.load(file)
        return payload if isinstance(payload, dict) else {}
    except Exception:  # noqa: BLE001
        return {}


def save_process_state(state: Dict[str, Dict]) -> None:
    with STATE_PATH.open("w", encoding="utf-8") as file:
        json.dump(state, file, ensure_ascii=False, indent=2)


def update_process_state(key: str, payload: Optional[Dict]) -> None:
    with PROCESS_LOCK:
        state = load_process_state()
        if payload is None:
            state.pop(key, None)
        else:
            state[key] = payload
        save_process_state(state)


def persisted_process_info(key: str) -> Optional[Dict]:
    state = load_process_state()
    item = state.get(key)
    if not item:
        return None
    pid = item.get("pid")
    if not isinstance(pid, int) or not pid_is_running(pid):
        update_process_state(key, None)
        return None
    return item


def run_shell_command(command_line: str, cwd: str, shell_name: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        command_line,
        shell=True,
        cwd=resolve_cwd(cwd),
        executable=shell_executable(shell_name),
        capture_output=True,
        text=True,
    )


def is_external_command_running(service: Dict, command: Dict) -> bool:
    status_command = command.get("statusCommand")
    if not status_command:
        return False
    completed = run_shell_command(status_command, command["cwd"], service["shell"])
    return completed.returncode == 0


def cleanup_processes() -> None:
    with PROCESS_LOCK:
        stale_keys = []
        for key, item in RUNNING_PROCESSES.items():
            if not process_is_running(item["process"]):
                log_handle = item.get("log_handle")
                if log_handle and not log_handle.closed:
                    log_handle.close()
                stale_keys.append(key)
        for key in stale_keys:
            RUNNING_PROCESSES.pop(key, None)
    for key in list(load_process_state().keys()):
        persisted_process_info(key)


def shell_executable(shell_name: str) -> str:
    return shell_name if shell_name.startswith("/") else f"/bin/{shell_name}"


def run_sync_command(service: Dict, command: Dict) -> Dict:
    started_at = time.time()
    completed = run_shell_command(command["command"], command["cwd"], service["shell"])
    ended_at = time.time()
    return {
        "mode": "sync",
        "returncode": completed.returncode,
        "stdout": completed.stdout,
        "stderr": completed.stderr,
        "durationSeconds": round(ended_at - started_at, 2),
    }


def run_background_command(service: Dict, command: Dict) -> Dict:
    if command.get("statusCommand"):
        if is_external_command_running(service, command):
            return {
                "mode": "background",
                "status": "already_running",
                "logFile": command.get("logFile"),
            }

        completed = run_shell_command(command["command"], command["cwd"], service["shell"])
        if completed.returncode != 0:
            return {
                "mode": "background",
                "status": "failed",
                "returncode": completed.returncode,
                "stdout": completed.stdout,
                "stderr": completed.stderr,
                "logFile": command.get("logFile"),
            }

        return {
            "mode": "background",
            "status": "started" if is_external_command_running(service, command) else "submitted",
            "stdout": completed.stdout,
            "stderr": completed.stderr,
            "logFile": command.get("logFile"),
        }

    cleanup_processes()
    key = process_key(service["name"], command["name"])
    persisted = persisted_process_info(key)
    if persisted:
        return {
            "mode": "background",
            "status": "already_running",
            "pid": persisted.get("pid"),
            "logFile": persisted.get("logFile"),
        }

    with PROCESS_LOCK:
        existing = RUNNING_PROCESSES.get(key)
        if existing and process_is_running(existing["process"]):
            return {
                "mode": "background",
                "status": "already_running",
                "pid": existing["process"].pid,
                "logFile": str(existing["log_path"]),
            }

        timestamp = time.strftime("%Y%m%d-%H%M%S")
        log_name = f"{service['name']}-{command['name']}-{timestamp}.log"
        safe_log_name = "".join(char if char.isalnum() or char in "-._" else "_" for char in log_name)
        log_path = RUNTIME_DIR / safe_log_name
        log_file = log_path.open("a", encoding="utf-8")
        proc = subprocess.Popen(
            command["command"],
            shell=True,
            cwd=resolve_cwd(command["cwd"]),
            executable=shell_executable(service["shell"]),
            stdout=log_file,
            stderr=subprocess.STDOUT,
            text=True,
            preexec_fn=os.setsid,
        )
        RUNNING_PROCESSES[key] = {
            "process": proc,
            "log_path": log_path,
            "log_handle": log_file,
            "started_at": time.time(),
            "service_name": service["name"],
            "command_name": command["name"],
        }
        update_process_state(
            key,
            {
                "pid": proc.pid,
                "logFile": str(log_path),
                "startedAt": time.time(),
                "serviceName": service["name"],
                "commandName": command["name"],
            },
        )

    return {
        "mode": "background",
        "status": "started",
        "pid": proc.pid,
        "logFile": str(log_path),
    }


def stop_background_command(service_name: str, command_name: str) -> Dict:
    cleanup_processes()
    services = load_services()
    service, command = find_command(services, service_name, command_name)

    if command.get("stopCommand"):
        completed = run_shell_command(command["stopCommand"], command["cwd"], service["shell"])
        stopped = not is_external_command_running(service, command)
        return {
            "stopped": stopped,
            "message": "process stopped" if stopped else "stop command submitted",
            "stdout": completed.stdout,
            "stderr": completed.stderr,
            "returncode": completed.returncode,
        }

    key = process_key(service_name, command_name)

    with PROCESS_LOCK:
        item = RUNNING_PROCESSES.get(key)
        item = RUNNING_PROCESSES.get(key)

    persisted = persisted_process_info(key)
    pid = None
    forced = False

    if item:
        proc = item["process"]
        pid = proc.pid
        if process_is_running(proc):
            os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
            try:
                proc.wait(timeout=10)
            except subprocess.TimeoutExpired:
                forced = True
                os.killpg(os.getpgid(proc.pid), signal.SIGKILL)
                proc.wait(timeout=5)

        log_handle = item.get("log_handle")
        if log_handle and not log_handle.closed:
            log_handle.close()
        with PROCESS_LOCK:
            RUNNING_PROCESSES.pop(key, None)
    elif persisted:
        pid = persisted["pid"]
        try:
            os.killpg(os.getpgid(pid), signal.SIGTERM)
            for _ in range(20):
                if not pid_is_running(pid):
                    break
                time.sleep(0.5)
            if pid_is_running(pid):
                forced = True
                os.killpg(os.getpgid(pid), signal.SIGKILL)
        except ProcessLookupError:
            pass
    else:
        return {"stopped": False, "message": "process not running"}

    update_process_state(key, None)

    if forced:
        return {"stopped": True, "message": "process force killed after timeout"}
    return {"stopped": True, "message": "process stopped"}


def run_all_background_commands() -> Dict:
    services = load_services()
    results = []
    for service in services:
        for command in service["commands"]:
            if not command["background"]:
                continue
            result = run_background_command(service, command)
            results.append(
                {
                    "serviceName": service["name"],
                    "commandName": command["name"],
                    **result,
                }
            )
    return {"results": results}


def stop_all_background_commands() -> Dict:
    cleanup_processes()
    with PROCESS_LOCK:
        keys = set(RUNNING_PROCESSES.keys())
    keys.update(load_process_state().keys())

    results = []
    for key in sorted(keys):
        service_name, command_name = key.split("::", 1)
        result = stop_background_command(service_name, command_name)
        results.append(
            {
                "serviceName": service_name,
                "commandName": command_name,
                **result,
            }
        )
    return {"results": results}


def service_snapshot() -> List[Dict]:
    cleanup_processes()
    services = load_services()
    snapshot = []
    with PROCESS_LOCK:
        for service in services:
            commands = []
            for command in service["commands"]:
                external_running = bool(command.get("statusCommand")) and is_external_command_running(service, command)
                key = process_key(service["name"], command["name"])
                item = RUNNING_PROCESSES.get(key)
                in_process_running = bool(item and process_is_running(item["process"]))
                persisted = persisted_process_info(key)
                running = external_running or in_process_running or bool(persisted)
                commands.append(
                    {
                        **command,
                        "running": running,
                        "pid": item["process"].pid if in_process_running else (persisted.get("pid") if persisted else None),
                        "logFile": command.get("logFile") or (str(item["log_path"]) if item else (persisted.get("logFile") if persisted else None)),
                    }
                )
            snapshot.append({**service, "commands": commands})
    return snapshot


def find_command(services: List[Dict], service_name: str, command_name: str) -> Tuple[Dict, Dict]:
    for service in services:
        if service["name"] != service_name:
            continue
        for command in service["commands"]:
            if command["name"] == command_name:
                return service, command
    raise KeyError(f"command not found: {service_name} / {command_name}")


def is_allowed_log_file(path: Path) -> bool:
    if path.is_file() and RUNTIME_DIR in path.parents:
        return True

    try:
        services = load_services()
    except Exception:  # noqa: BLE001
        return False

    for service in services:
        for command in service["commands"]:
            log_file = command.get("logFile")
            if not log_file:
                continue
            if Path(log_file).resolve() == path.resolve() and path.is_file():
                return True
    return False


class ServiceManagerHandler(BaseHTTPRequestHandler):
    def _send_json(self, status: int, payload: Dict) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _read_json_body(self) -> Dict:
        length = int(self.headers.get("Content-Length", "0"))
        if length == 0:
            return {}
        raw = self.rfile.read(length)
        return json.loads(raw.decode("utf-8"))

    def do_GET(self) -> None:
        parsed = urlparse(self.path)

        if parsed.path == "/":
            content = HTML_PATH.read_text(encoding="utf-8").encode("utf-8")
            self.send_response(HTTPStatus.OK)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()
            self.wfile.write(content)
            return

        if parsed.path == "/api/services":
            self._send_json(HTTPStatus.OK, {"services": service_snapshot()})
            return

        if parsed.path == "/api/log":
            query = parse_qs(parsed.query)
            log_file = query.get("file", [""])[0]
            if not log_file:
                self._send_json(HTTPStatus.BAD_REQUEST, {"error": "file is required"})
                return
            path = Path(log_file)
            if not is_allowed_log_file(path):
                self._send_json(HTTPStatus.BAD_REQUEST, {"error": "invalid log file"})
                return
            content = path.read_text(encoding="utf-8", errors="replace")
            self._send_json(HTTPStatus.OK, {"content": content[-20000:]})
            return

        self._send_json(HTTPStatus.NOT_FOUND, {"error": "not found"})

    def do_POST(self) -> None:
        parsed = urlparse(self.path)
        try:
            body = self._read_json_body()

            if parsed.path == "/api/run":
                services = load_services()
                service, command = find_command(services, body["serviceName"], body["commandName"])
                if command["background"]:
                    result = run_background_command(service, command)
                else:
                    result = run_sync_command(service, command)
                self._send_json(HTTPStatus.OK, result)
                return

            if parsed.path == "/api/stop":
                result = stop_background_command(body["serviceName"], body["commandName"])
                self._send_json(HTTPStatus.OK, result)
                return

            if parsed.path == "/api/run-all":
                result = run_all_background_commands()
                self._send_json(HTTPStatus.OK, result)
                return

            if parsed.path == "/api/stop-all":
                result = stop_all_background_commands()
                self._send_json(HTTPStatus.OK, result)
                return

        except KeyError as error:
            self._send_json(HTTPStatus.BAD_REQUEST, {"error": str(error)})
            return
        except subprocess.TimeoutExpired:
            self._send_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"error": "failed to stop process within timeout"})
            return
        except Exception as error:  # noqa: BLE001
            self._send_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"error": str(error)})
            return

        self._send_json(HTTPStatus.NOT_FOUND, {"error": "not found"})

    def log_message(self, format: str, *args) -> None:
        return


def main() -> None:
    port = int(os.environ.get("SERVICE_MANAGER_PORT", "18080"))
    host = os.environ.get("SERVICE_MANAGER_HOST", "127.0.0.1")
    server = ThreadingHTTPServer((host, port), ServiceManagerHandler)
    print(f"Service manager running at http://{host}:{port}")
    print(f"Config file: {CONFIG_PATH}")
    server.serve_forever()


if __name__ == "__main__":
    main()

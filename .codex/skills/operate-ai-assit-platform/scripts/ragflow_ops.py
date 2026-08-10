#!/usr/bin/env python3
"""RAGFlow diagnostics and guarded operations for ai-assit-platform."""

from __future__ import annotations

import argparse
import json
import os
import shlex
import subprocess
import sys
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlencode, urlsplit
from urllib.request import Request, urlopen

from db_safe_query import find_repo_root, mysql_capture, resolve_connection

SENSITIVE_PARTS = ("password", "passwd", "secret", "token", "apikey", "api_key", "accesskey", "auth")
RAGFLOW_SETTING_KEY = "chat.engine.kb.client.list"


class RagflowError(RuntimeError):
    pass


def sanitize(value: Any, parent_key: str = "") -> Any:
    if isinstance(value, dict):
        result: dict[str, Any] = {}
        for key, item in value.items():
            normalized = str(key).replace("-", "_").lower()
            if any(part in normalized for part in SENSITIVE_PARTS):
                result[str(key)] = "<redacted>"
            else:
                result[str(key)] = sanitize(item, normalized)
        return result
    if isinstance(value, list):
        return [sanitize(item, parent_key) for item in value]
    return value


def print_json(value: Any) -> None:
    print(json.dumps(sanitize(value), ensure_ascii=False, indent=2))


def validate_base_url(value: str) -> str:
    normalized = value.strip().rstrip("/")
    parsed = urlsplit(normalized)
    if parsed.scheme not in {"http", "https"} or not parsed.hostname:
        raise RagflowError("RAGFLOW_BASE_URL must be an absolute http(s) URL")
    if parsed.username or parsed.password:
        raise RagflowError("Credentials must not be embedded in RAGFLOW_BASE_URL")
    return normalized


def load_project_client(repo: Path, config_source: str) -> tuple[str, str, str]:
    connection = resolve_connection(repo, "user", config_source=config_source)
    sql = (
        "SELECT setting_value FROM system_settings "
        f"WHERE setting_key = '{RAGFLOW_SETTING_KEY}' AND enabled = 1 LIMIT 1"
    )
    raw = mysql_capture(connection, sql, skip_headers=True).strip()
    if not raw:
        raise RagflowError(f"Enabled system setting not found: {RAGFLOW_SETTING_KEY}")
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as error:
        raise RagflowError("RAGFlow system setting is not valid JSON") from error
    if not isinstance(payload, list):
        raise RagflowError("RAGFlow system setting must be a JSON array")

    candidates = []
    for item in payload:
        if not isinstance(item, dict):
            continue
        key = str(item.get("key", "")).lower()
        item_type = item.get("type")
        if key == "ragflow" or item_type in {2, "2", "RAGFLOW", "ragflow"}:
            candidates.append(item)
    if len(candidates) != 1:
        raise RagflowError(f"Expected one enabled RAGFlow client, found {len(candidates)}")

    item = candidates[0]
    base_url = item.get("url") or item.get("baseUrl") or item.get("base_url")
    auth = item.get("auth")
    api_key = None
    if isinstance(auth, dict):
        api_key = auth.get("value") or auth.get("token")
    api_key = api_key or item.get("apiKey") or item.get("apikey") or item.get("api_key")
    if not isinstance(base_url, str) or not base_url.strip():
        raise RagflowError("RAGFlow client URL is missing")
    if not isinstance(api_key, str) or not api_key.strip():
        raise RagflowError("RAGFlow bearer credential is missing")
    return validate_base_url(base_url), api_key.strip(), connection.config_source


def resolve_api_config(args: argparse.Namespace, repo: Path) -> tuple[str, str, str]:
    if args.project_db:
        return load_project_client(repo, args.db_config_source)
    base_url = args.base_url or os.environ.get("RAGFLOW_BASE_URL")
    api_key = os.environ.get("RAGFLOW_API_KEY")
    if args.api_key_file:
        api_key = Path(args.api_key_file).expanduser().read_text(encoding="utf-8").strip()
    if not base_url:
        raise RagflowError("Set RAGFLOW_BASE_URL or use --project-db")
    if not api_key:
        raise RagflowError("Set RAGFLOW_API_KEY, use --api-key-file, or use --project-db")
    return validate_base_url(base_url), api_key.strip(), "environment"


def api_request(
    base_url: str,
    api_key: str,
    method: str,
    path: str,
    body: dict[str, Any] | None,
    timeout: float,
) -> Any:
    data = None if body is None else json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = Request(
        base_url + path,
        data=data,
        method=method,
        headers={
            "Accept": "application/json",
            "Content-Type": "application/json",
            "Authorization": f"Bearer {api_key}",
        },
    )
    try:
        with urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8", errors="replace")
    except HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        try:
            detail = sanitize(json.loads(raw))
        except json.JSONDecodeError:
            detail = raw[:1000]
        raise RagflowError(f"RAGFlow HTTP {error.code}: {detail}") from error
    except URLError as error:
        raise RagflowError(f"RAGFlow connection failed: {error.reason}") from error
    if not raw.strip():
        return {"status": "empty-response"}
    try:
        payload = json.loads(raw)
    except json.JSONDecodeError as error:
        raise RagflowError("RAGFlow returned non-JSON content") from error
    if isinstance(payload, dict):
        code = payload.get("code")
        if code not in (None, 0, "0"):
            raise RagflowError(f"RAGFlow API error: {sanitize(payload)}")
    return payload


def compose_file(home_value: str | None) -> Path:
    if not home_value:
        raise RagflowError("Set RAGFLOW_HOME for local Compose operations")
    home = Path(home_value).expanduser().resolve()
    candidates = [
        home / "docker" / "docker-compose.yml",
        home / "docker" / "docker-compose.yaml",
        home / "docker" / "compose.yml",
        home / "docker" / "compose.yaml",
        home / "docker-compose.yml",
        home / "docker-compose.yaml",
        home / "compose.yml",
        home / "compose.yaml",
    ]
    for candidate in candidates:
        if candidate.is_file():
            content = candidate.read_text(encoding="utf-8", errors="replace").lower()
            if "ragflow" not in content:
                raise RagflowError(f"Compose file does not appear to define RAGFlow: {candidate}")
            return candidate
    raise RagflowError(f"No Compose file found below RAGFLOW_HOME={home}")


def run_compose(compose: Path, arguments: list[str], capture: bool = False) -> int:
    command = ["docker", "compose", "-f", str(compose), *arguments]
    print("Running: " + shlex.join(command))
    if capture:
        completed = subprocess.run(
            command,
            cwd=compose.parent,
            text=True,
            capture_output=True,
            check=False,
        )
        if completed.stdout:
            print(completed.stdout, end="" if completed.stdout.endswith("\n") else "\n")
        if completed.stderr:
            print(completed.stderr, end="" if completed.stderr.endswith("\n") else "\n", file=sys.stderr)
        return completed.returncode
    return subprocess.run(command, cwd=compose.parent, check=False).returncode


def add_api_parsers(subparsers: argparse._SubParsersAction) -> None:
    subparsers.add_parser("health", help="Authenticate and perform a minimal Dataset list request")

    datasets = subparsers.add_parser("datasets", help="List Datasets")
    datasets.add_argument("--page", type=int, default=1)
    datasets.add_argument("--page-size", type=int, default=30)
    datasets.add_argument("--name")
    datasets.add_argument("--include-parsing-status", action="store_true")

    subparsers.add_parser("models", help="List configured embedding models")

    documents = subparsers.add_parser("documents", help="List documents in one Dataset")
    documents.add_argument("--dataset-id", required=True)
    documents.add_argument("--page", type=int, default=1)
    documents.add_argument("--page-size", type=int, default=30)

    retrieve = subparsers.add_parser("retrieve", help="Run a retrieval check")
    retrieve.add_argument("--dataset-id", required=True)
    retrieve.add_argument("--question", required=True)
    retrieve.add_argument("--page-size", type=int, default=5)
    retrieve.add_argument("--similarity-threshold", type=float, default=0.2)
    retrieve.add_argument("--vector-similarity-weight", type=float, default=0.4)
    retrieve.add_argument("--top-k", type=int, default=1024)

    delete = subparsers.add_parser("delete-dataset", help="Guarded direct Dataset deletion for recovery")
    delete.add_argument("--dataset-id", required=True)
    delete.add_argument("--confirm-id")
    delete.add_argument("--execute", action="store_true")


def add_local_parsers(subparsers: argparse._SubParsersAction) -> None:
    subparsers.add_parser("local-status", help="Run docker compose ps for RAGFlow")
    subparsers.add_parser("local-start", help="Start an owned RAGFlow Compose stack")

    logs = subparsers.add_parser("local-logs", help="Read RAGFlow Compose logs")
    logs.add_argument("--lines", type=int, default=200)
    logs.add_argument("--follow", action="store_true")
    logs.add_argument("--service")

    stop = subparsers.add_parser("local-stop", help="Stop an owned RAGFlow Compose stack")
    stop.add_argument("--execute", action="store_true")


def handle_api(args: argparse.Namespace, repo: Path) -> int:
    base_url, api_key, config_source = resolve_api_config(args, repo)
    if args.action == "health":
        payload = api_request(base_url, api_key, "GET", "/api/v1/datasets?page=1&page_size=1", None, args.timeout)
        data = payload.get("data") if isinstance(payload, dict) else payload
        count = len(data) if isinstance(data, list) else None
        print_json(
            {
                "ok": True,
                "baseUrl": base_url,
                "configSource": config_source,
                "sampleDatasetCount": count,
            }
        )
        return 0
    if args.action == "datasets":
        query: dict[str, str] = {
            "page": str(max(1, args.page)),
            "page_size": str(max(1, args.page_size)),
            "include_parsing_status": str(bool(args.include_parsing_status)).lower(),
        }
        if args.name:
            query["name"] = args.name
        print_json(api_request(base_url, api_key, "GET", "/api/v1/datasets?" + urlencode(query), None, args.timeout))
        return 0
    if args.action == "models":
        print_json(api_request(base_url, api_key, "GET", "/api/v1/models?type=embedding", None, args.timeout))
        return 0
    if args.action == "documents":
        dataset_id = quote(args.dataset_id, safe="")
        query = urlencode({"page": max(1, args.page), "page_size": max(1, args.page_size)})
        path = f"/api/v1/datasets/{dataset_id}/documents?{query}"
        print_json(api_request(base_url, api_key, "GET", path, None, args.timeout))
        return 0
    if args.action == "retrieve":
        body = {
            "question": args.question,
            "dataset_ids": [args.dataset_id],
            "page": 1,
            "page_size": max(1, args.page_size),
            "similarity_threshold": args.similarity_threshold,
            "vector_similarity_weight": args.vector_similarity_weight,
            "top_k": max(1, args.top_k),
            "keyword": True,
            "highlight": True,
            "use_kg": False,
            "toc_enhance": False,
        }
        print_json(api_request(base_url, api_key, "POST", "/api/v1/retrieval", body, args.timeout))
        return 0
    if args.action == "delete-dataset":
        preview = {
            "method": "DELETE",
            "baseUrl": base_url,
            "configSource": config_source,
            "path": "/api/v1/datasets",
            "ids": [args.dataset_id],
        }
        print_json(preview)
        if not args.execute:
            print("Not executed. Verify local ai_kb_store mappings, backup, and cleanup; then add --execute --confirm-id.")
            return 0
        if args.confirm_id != args.dataset_id:
            raise RagflowError("--confirm-id must exactly match --dataset-id")
        if args.project_db and args.db_config_source == "auto" and config_source.startswith("local:"):
            raise RagflowError(
                "Auto configuration fell back to the repository datasource; select --db-config-source local "
                "explicitly only after verifying that RAGFlow target"
            )
        print_json(api_request(base_url, api_key, "DELETE", "/api/v1/datasets", {"ids": [args.dataset_id]}, args.timeout))
        return 0
    raise RagflowError(f"Unsupported API action: {args.action}")


def handle_local(args: argparse.Namespace) -> int:
    compose = compose_file(args.ragflow_home or os.environ.get("RAGFLOW_HOME"))
    if args.action == "local-status":
        return run_compose(compose, ["ps"], capture=True)
    if args.action == "local-start":
        return run_compose(compose, ["up", "-d"])
    if args.action == "local-logs":
        command = ["logs", "--tail", str(max(1, args.lines))]
        if args.follow:
            command.append("--follow")
        if args.service:
            command.append(args.service)
        return run_compose(compose, command)
    if args.action == "local-stop":
        print(f"Target Compose file: {compose}")
        if not args.execute:
            print("Not executed. Confirm the stack has no other consumers, then add --execute.")
            return 0
        return run_compose(compose, ["down"])
    raise RagflowError(f"Unsupported local action: {args.action}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", help="ai-assit-platform repository root")
    parser.add_argument("--project-db", action="store_true", help="Resolve RAGFlow URL/key from the enabled project setting")
    parser.add_argument(
        "--db-config-source",
        choices=("auto", "local", "nacos"),
        default="auto",
        help="Datasource source used by --project-db; auto prefers reachable Nacos",
    )
    parser.add_argument("--base-url", help="RAGFlow root URL; prefer RAGFLOW_BASE_URL")
    parser.add_argument("--api-key-file", help="Protected file containing only the API key")
    parser.add_argument("--timeout", type=float, default=30.0)
    parser.add_argument("--ragflow-home", help="Owned local RAGFlow checkout; prefer RAGFLOW_HOME")
    subparsers = parser.add_subparsers(dest="action", required=True)
    add_api_parsers(subparsers)
    add_local_parsers(subparsers)
    args = parser.parse_args()

    try:
        if args.action.startswith("local-"):
            return handle_local(args)
        repo = find_repo_root(args.repo)
        return handle_api(args, repo)
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

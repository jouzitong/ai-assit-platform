#!/usr/bin/env python3
"""Guarded MySQL client for ai-assit-platform development data.

Reads are allowed by default. Persistent DML requires explicit write, execute,
commit, database confirmation, and reason flags. Credentials are never passed
on the command line or printed.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import secrets
import shutil
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.parse import parse_qs, urlencode, urlsplit
from urllib.request import Request, urlopen

SERVICE_CONFIGS = {
    "user": "app/app-platform-user/boot/src/main/resources/application.yml",
    "chat": "app/app-platform-chat/config/application.yml",
    "db-engine": "app/app-platform-db-engine/boot/src/main/resources/application.yml",
    "render": "app/app-platform-render/boot/src/main/resources/application.yml",
    "file": "app/app-platform-file/boot/src/main/resources/application.yml",
}

BOOTSTRAP_CONFIGS = {
    "user": "app/app-platform-user/boot/src/main/resources/bootstrap.yml",
    "chat": "app/app-platform-chat/config/bootstrap.yml",
    "db-engine": "app/app-platform-db-engine/boot/src/main/resources/bootstrap.yml",
    "render": "app/app-platform-render/boot/src/main/resources/bootstrap.yml",
    "file": "app/app-platform-file/boot/src/main/resources/bootstrap.yml",
}

EXPECTED_DATABASES = {
    "user": "ai_assist_user",
    "chat": "ai_assist_chat_v3",
    "db-engine": "ai_assist_db_engine",
    "render": "ai_assist_render",
}

READ_VERBS = {"SELECT", "SHOW", "DESCRIBE", "DESC", "EXPLAIN"}
WRITE_VERBS = {"INSERT", "UPDATE", "DELETE"}
PLACEHOLDER = re.compile(r"\$\{([^{}:$]+)(?::([^{}]*))?}")
LEADING_COMMENTS = re.compile(r"\A(?:\s+|--[^\n]*(?:\n|\Z)|#[^\n]*(?:\n|\Z)|/\*.*?\*/)*", re.DOTALL)


class ConfigurationError(RuntimeError):
    pass


@dataclass(frozen=True)
class Connection:
    host: str
    port: int
    user: str
    password: str
    database: str
    use_ssl: bool | None = None
    config_source: str = "local"

    def public_dict(self) -> dict[str, object]:
        return {
            "host": self.host,
            "port": self.port,
            "database": self.database,
            "userConfigured": bool(self.user),
            "passwordConfigured": bool(self.password),
            "useSsl": self.use_ssl,
            "configSource": self.config_source,
        }


@dataclass(frozen=True)
class ClientBackend:
    kind: str
    executable: str
    container: str | None = None

    def summary(self) -> str:
        if self.kind == "host":
            return f"host:{self.executable}"
        return f"docker:{self.container}:{self.executable}"


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
    raise ConfigurationError("Repository root not found; pass --repo explicitly.")


def unquote(value: str) -> str:
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value


def first_yaml_scalar(text: str, key: str) -> str | None:
    match = re.search(rf"(?m)^[ \t]+{re.escape(key)}:[ \t]*(.*?)[ \t]*$", text)
    if not match:
        return None
    value = match.group(1)
    if " #" in value:
        value = value.split(" #", 1)[0]
    return unquote(value)


def yaml_path_scalar(text: str, path: tuple[str, ...]) -> str | None:
    stack: list[tuple[int, str]] = []
    for raw_line in text.splitlines():
        if not raw_line.strip() or raw_line.lstrip().startswith("#"):
            continue
        match = re.match(r"^(\s*)([A-Za-z0-9_.-]+):(?:[ \t]*(.*))?$", raw_line)
        if not match:
            continue
        indent = len(match.group(1).replace("\t", "    "))
        key = match.group(2)
        raw_value = match.group(3) or ""
        while stack and stack[-1][0] >= indent:
            stack.pop()
        current = tuple(item[1] for item in stack) + (key,)
        value = raw_value.strip()
        if value and current == path:
            if " #" in value:
                value = value.split(" #", 1)[0]
            return unquote(value)
        if not value:
            stack.append((indent, key))
    return None


def database_name_from_service_config(path: Path) -> str | None:
    if not path.is_file():
        return None
    text = path.read_text(encoding="utf-8")
    return yaml_path_scalar(text, ("database", "name"))


def expand_placeholders(value: str, extra: dict[str, str]) -> str:
    result = value
    for _ in range(12):
        match = PLACEHOLDER.search(result)
        if not match:
            return result
        name, default = match.group(1), match.group(2)
        replacement = extra.get(name)
        if replacement is None:
            replacement = os.environ.get(name)
        if replacement is None:
            replacement = default
        if replacement is None:
            raise ConfigurationError(f"Unresolved configuration placeholder: {name}")
        result = result[: match.start()] + replacement + result[match.end() :]
    raise ConfigurationError("Too many nested configuration placeholders")


def fetch_nacos_common(repo: Path, service: str) -> str:
    bootstrap_relative = BOOTSTRAP_CONFIGS.get(service)
    if not bootstrap_relative:
        raise ConfigurationError(f"No Nacos bootstrap mapping for {service}")
    bootstrap_path = repo / bootstrap_relative
    if not bootstrap_path.is_file():
        raise ConfigurationError(f"Nacos bootstrap configuration not found: {bootstrap_path}")
    text = bootstrap_path.read_text(encoding="utf-8")
    prefix = ("spring", "cloud", "nacos", "config")
    server = yaml_path_scalar(text, (*prefix, "server-addr"))
    context = yaml_path_scalar(text, (*prefix, "context-path")) or "/nacos"
    namespace = yaml_path_scalar(text, (*prefix, "namespace")) or ""
    group = yaml_path_scalar(text, (*prefix, "group")) or "DEFAULT_GROUP"
    username = yaml_path_scalar(text, (*prefix, "username")) or ""
    password = yaml_path_scalar(text, (*prefix, "password")) or ""
    if not server:
        raise ConfigurationError("Nacos config server address is missing")
    server = expand_placeholders(server, {})
    username = expand_placeholders(username, {}) if username else ""
    password = expand_placeholders(password, {}) if password else ""
    base = server if server.startswith(("http://", "https://")) else "http://" + server
    base = base.rstrip("/") + "/" + context.strip("/")

    token = None
    if username:
        login_data = urlencode({"username": username, "password": password}).encode("utf-8")
        login = Request(base + "/v1/auth/login", data=login_data, method="POST")
        try:
            with urlopen(login, timeout=3) as response:
                payload = json.loads(response.read().decode("utf-8", errors="replace"))
            if isinstance(payload, dict):
                token = payload.get("accessToken") or payload.get("access_token")
        except (HTTPError, URLError, json.JSONDecodeError):
            token = None

    query = {"dataId": "common.yaml", "group": group}
    if namespace:
        query["tenant"] = namespace
    if token:
        query["accessToken"] = str(token)
    request = Request(base + "/v1/cs/configs?" + urlencode(query), method="GET")
    try:
        with urlopen(request, timeout=4) as response:
            content = response.read().decode("utf-8", errors="replace")
    except HTTPError as error:
        raise ConfigurationError(f"Nacos common.yaml request failed with HTTP {error.code}") from error
    except URLError as error:
        raise ConfigurationError(f"Nacos common.yaml is unreachable: {error.reason}") from error
    if not content.strip() or "config data not exist" in content.lower():
        raise ConfigurationError("Nacos common.yaml is empty or missing")
    return content


def datasource_config_text(repo: Path, service: str, config_source: str) -> tuple[str, str]:
    local_path = repo / "app/config/application-common.yaml"
    if not local_path.is_file():
        raise ConfigurationError(f"Shared datasource configuration not found: {local_path}")
    if config_source in {"auto", "nacos"}:
        try:
            remote = fetch_nacos_common(repo, service)
            if yaml_path_scalar(remote, ("spring", "datasource", "url")):
                return remote, "nacos:common.yaml"
            if config_source == "nacos":
                raise ConfigurationError("Nacos common.yaml has no spring.datasource.url")
        except ConfigurationError:
            if config_source == "nacos":
                raise
    return local_path.read_text(encoding="utf-8"), "local:app/config/application-common.yaml"


def resolve_connection(
    repo: Path,
    service: str,
    database_override: str | None = None,
    config_source: str = "auto",
) -> Connection:
    if service not in SERVICE_CONFIGS:
        raise ConfigurationError(f"Unsupported service: {service}")
    if config_source not in {"auto", "local", "nacos"}:
        raise ConfigurationError("config_source must be auto, local, or nacos")
    common_text, resolved_source = datasource_config_text(repo, service, config_source)

    service_path = repo / SERVICE_CONFIGS[service]
    database = database_override or os.environ.get("AI_ASSIST_DB_NAME")
    if not database:
        database = database_name_from_service_config(service_path)
    if not database:
        database = EXPECTED_DATABASES.get(service)
    if not database:
        raise ConfigurationError(f"Database name is not defined for {service}; pass --database")
    if not re.fullmatch(r"[A-Za-z0-9_]+", database):
        raise ConfigurationError("Database name must contain only letters, digits, and underscores")

    raw_url = (
        os.environ.get("AI_ASSIST_DB_URL")
        or os.environ.get("SPRING_DATASOURCE_URL")
        or yaml_path_scalar(common_text, ("spring", "datasource", "url"))
        or first_yaml_scalar(common_text, "url")
    )
    raw_user = (
        os.environ.get("AI_ASSIST_DB_USER")
        or os.environ.get("SPRING_DATASOURCE_USERNAME")
        or yaml_path_scalar(common_text, ("spring", "datasource", "username"))
        or first_yaml_scalar(common_text, "username")
    )
    raw_password = (
        os.environ.get("AI_ASSIST_DB_PASSWORD")
        or os.environ.get("SPRING_DATASOURCE_PASSWORD")
        or yaml_path_scalar(common_text, ("spring", "datasource", "password"))
        or first_yaml_scalar(common_text, "password")
    )
    if not raw_url or raw_user is None or raw_password is None:
        raise ConfigurationError("Datasource URL, username, or password is not configured")

    extra = {"database.name": database}
    url = expand_placeholders(unquote(raw_url), extra)
    user = expand_placeholders(unquote(raw_user), extra)
    password = expand_placeholders(unquote(raw_password), extra)
    if not url.startswith("jdbc:mysql://"):
        raise ConfigurationError("Only jdbc:mysql:// datasource URLs are supported")
    parsed = urlsplit(url[len("jdbc:") :])
    if not parsed.hostname:
        raise ConfigurationError("Datasource host is missing")
    url_database = parsed.path.lstrip("/")
    if url_database and url_database != database:
        raise ConfigurationError(f"Resolved URL database {url_database!r} does not match {database!r}")
    query = parse_qs(parsed.query)
    ssl_value = (query.get("useSSL") or query.get("useSsl") or [None])[0]
    use_ssl = None if ssl_value is None else ssl_value.lower() == "true"
    if os.environ.get("AI_ASSIST_DB_URL") or os.environ.get("SPRING_DATASOURCE_URL"):
        resolved_source = "environment"
    return Connection(parsed.hostname, parsed.port or 3306, user, password, database, use_ssl, resolved_source)


def resolve_client_backend() -> ClientBackend:
    host_error = None
    candidates: list[str] = []
    explicit = os.environ.get("AI_ASSIST_MYSQL_CLIENT")
    if explicit:
        candidates.append(str(Path(explicit).expanduser()))
    path_binary = shutil.which("mysql") or shutil.which("mariadb")
    if path_binary:
        candidates.append(path_binary)
    for pattern in (
        "/opt/homebrew/Cellar/mysql/*/bin/mysql",
        "/opt/homebrew/Cellar/mysql-client/*/bin/mysql",
        "/usr/local/Cellar/mysql/*/bin/mysql",
        "/usr/local/Cellar/mysql-client/*/bin/mysql",
    ):
        candidates.extend(str(path) for path in sorted(Path("/").glob(pattern.lstrip("/")), reverse=True))
    for binary in dict.fromkeys(candidates):
        if not Path(binary).is_file():
            continue
        probe = subprocess.run([binary, "--version"], capture_output=True, text=True, check=False)
        if probe.returncode == 0:
            return ClientBackend("host", binary)
        host_error = (probe.stderr or probe.stdout).strip().splitlines()[0] if (probe.stderr or probe.stdout).strip() else "version probe failed"

    docker = shutil.which("docker")
    container = os.environ.get("AI_ASSIST_MYSQL_CLIENT_CONTAINER", "my-mysql")
    if docker and container:
        probe = subprocess.run(
            [docker, "exec", container, "sh", "-lc", "command -v mysql || command -v mariadb"],
            capture_output=True,
            text=True,
            check=False,
        )
        executable = probe.stdout.strip().splitlines()[-1] if probe.returncode == 0 and probe.stdout.strip() else None
        if executable:
            return ClientBackend("docker", executable, container)

    detail = f"; host client error: {host_error}" if host_error else ""
    raise ConfigurationError(
        "No working mysql/mariadb client found. Set AI_ASSIST_MYSQL_CLIENT to a working binary, fix the host "
        f"client, or set AI_ASSIST_MYSQL_CLIENT_CONTAINER to a running client container{detail}"
    )


def mysql_backend_summary() -> str:
    return resolve_client_backend().summary()


def host_mysql_command(
    connection: Connection,
    backend: ClientBackend,
    skip_headers: bool,
    include_database: bool,
) -> tuple[list[str], dict[str, str]]:
    command = [
        backend.executable,
        "--protocol=TCP",
        f"--host={connection.host}",
        f"--port={connection.port}",
        f"--user={connection.user}",
        "--connect-timeout=8",
        "--batch",
        "--raw",
    ]
    if include_database:
        command.append(f"--database={connection.database}")
    if skip_headers:
        command.append("--skip-column-names")
    env = os.environ.copy()
    env["MYSQL_PWD"] = connection.password
    return command, env


def option_value(value: str) -> str:
    if "\n" in value or "\r" in value:
        raise ConfigurationError("MySQL option values must not contain newlines")
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def docker_mysql_run(
    connection: Connection,
    backend: ClientBackend,
    sql: str,
    skip_headers: bool,
    include_database: bool,
) -> subprocess.CompletedProcess[str]:
    assert backend.container
    docker = shutil.which("docker")
    if not docker:
        raise ConfigurationError("docker command is unavailable")
    option_path = f"/tmp/ai-assist-mysql-{os.getpid()}-{secrets.token_hex(6)}.cnf"
    option_lines = [
        "[client]",
        f"host={option_value(connection.host)}",
        f"port={connection.port}",
        f"user={option_value(connection.user)}",
        f"password={option_value(connection.password)}",
    ]
    if include_database:
        option_lines.append(f"database={option_value(connection.database)}")
    option_lines.extend(["protocol=TCP", "connect-timeout=8", ""])
    option_file = "\n".join(option_lines)
    create = subprocess.run(
        [docker, "exec", "-i", backend.container, "sh", "-c", 'umask 077; cat > "$1"', "sh", option_path],
        input=option_file,
        capture_output=True,
        text=True,
        check=False,
    )
    if create.returncode != 0:
        raise RuntimeError(create.stderr.strip() or "failed to create protected MySQL client option file")
    try:
        command = [
            docker,
            "exec",
            "-i",
            backend.container,
            backend.executable,
            f"--defaults-extra-file={option_path}",
            "--batch",
            "--raw",
        ]
        if skip_headers:
            command.append("--skip-column-names")
        return subprocess.run(command, input=sql + "\n", capture_output=True, text=True, check=False)
    finally:
        subprocess.run(
            [docker, "exec", backend.container, "rm", "-f", option_path],
            capture_output=True,
            text=True,
            check=False,
        )


def mysql_run(
    connection: Connection,
    sql: str,
    skip_headers: bool,
    include_database: bool = True,
) -> subprocess.CompletedProcess[str]:
    backend = resolve_client_backend()
    if backend.kind == "docker":
        return docker_mysql_run(connection, backend, sql, skip_headers, include_database)
    command, env = host_mysql_command(connection, backend, skip_headers, include_database)
    return subprocess.run(command, env=env, input=sql + "\n", capture_output=True, text=True, check=False)


def mysql_capture(
    connection: Connection,
    sql: str,
    skip_headers: bool = True,
    include_database: bool = True,
) -> str:
    completed = mysql_run(connection, sql, skip_headers, include_database)
    if completed.returncode != 0:
        message = completed.stderr.strip() or "mysql command failed"
        raise RuntimeError(message)
    return completed.stdout


def run_mysql(connection: Connection, sql: str) -> int:
    completed = mysql_run(connection, sql, skip_headers=False)
    if completed.stdout:
        print(completed.stdout, end="" if completed.stdout.endswith("\n") else "\n")
    if completed.stderr:
        print(completed.stderr, end="" if completed.stderr.endswith("\n") else "\n", file=sys.stderr)
    return completed.returncode


def normalized_statement(sql: str) -> tuple[str, str]:
    stripped = LEADING_COMMENTS.sub("", sql).strip()
    if not stripped:
        raise ValueError("SQL is empty")
    core = stripped[:-1].rstrip() if stripped.endswith(";") else stripped
    if ";" in core:
        raise ValueError("Only one SQL statement is allowed")
    match = re.match(r"([A-Za-z]+)", core)
    if not match:
        raise ValueError("Cannot determine SQL statement type")
    return match.group(1).upper(), core


def validate_target_scope(sql: str, database: str) -> None:
    qualified = re.compile(
        r"(?i)\b(?:FROM|JOIN|UPDATE|INTO|DELETE\s+FROM)\s+`?([A-Za-z0-9_]+)`?\s*\."
    )
    for match in qualified.finditer(sql):
        if match.group(1).lower() != database.lower():
            raise ValueError(f"Cross-database reference is not allowed: {match.group(1)}")


def validate_read(sql: str, database: str) -> str:
    verb, core = normalized_statement(sql)
    if verb not in READ_VERBS:
        raise ValueError(f"Read mode only allows {', '.join(sorted(READ_VERBS))}; got {verb}")
    if re.search(r"(?i)\b(?:INTO\s+(?:OUTFILE|DUMPFILE)|FOR\s+UPDATE|LOCK\s+IN\s+SHARE\s+MODE)\b", core):
        raise ValueError("File writes and locking SELECT variants are not allowed")
    validate_target_scope(core, database)
    return core


def validate_write(sql: str, database: str) -> tuple[str, str]:
    verb, core = normalized_statement(sql)
    if verb not in WRITE_VERBS:
        raise ValueError("Write mode allows one INSERT, UPDATE, or DELETE statement; DDL is forbidden")
    if verb in {"UPDATE", "DELETE"} and not re.search(r"(?i)\bWHERE\b", core):
        raise ValueError(f"{verb} requires a WHERE clause")
    validate_target_scope(core, database)
    return verb, core


def read_sql(args: argparse.Namespace) -> str:
    if bool(args.sql) == bool(args.file):
        raise ValueError("Pass exactly one of --sql or --file")
    if args.file:
        return Path(args.file).expanduser().read_text(encoding="utf-8")
    return args.sql


def print_targets(repo: Path, as_json: bool) -> None:
    rows = []
    for service, config in SERVICE_CONFIGS.items():
        configured = database_name_from_service_config(repo / config)
        rows.append(
            {
                "service": service,
                "database": configured or EXPECTED_DATABASES.get(service),
                "config": config,
            }
        )
    if as_json:
        print(json.dumps(rows, ensure_ascii=False, indent=2))
        return
    print(f"{'SERVICE':<12} {'DATABASE':<24} CONFIG")
    for row in rows:
        print(f"{row['service']:<12} {(row['database'] or '<runtime>'):<24} {row['config']}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repo", help="Repository root; auto-detected by default")
    parser.add_argument("--service", choices=sorted(SERVICE_CONFIGS), default="chat")
    parser.add_argument("--database", help="Explicit database override")
    parser.add_argument(
        "--config-source",
        choices=("auto", "local", "nacos"),
        default="auto",
        help="Datasource source; auto prefers reachable Nacos common.yaml",
    )
    parser.add_argument("--list-targets", action="store_true")
    parser.add_argument("--json", action="store_true", help="JSON output for target/config summaries")
    parser.add_argument("--connection-test", action="store_true")
    parser.add_argument("--sql")
    parser.add_argument("--file", help="UTF-8 SQL file containing one statement")
    parser.add_argument("--dry-run", action="store_true", help="Validate a read without connecting")
    parser.add_argument("--allow-write", action="store_true")
    parser.add_argument("--execute", action="store_true", help="Execute approved DML; preview is the default")
    parser.add_argument("--commit", action="store_true", help="Commit executed DML; otherwise roll it back")
    parser.add_argument("--confirm-database", help="Must exactly match the target database for writes")
    parser.add_argument("--confirm-host", help="Must exactly match the resolved datasource host for writes")
    parser.add_argument("--reason", help="Short reason or fixture/test identifier for writes")
    args = parser.parse_args()

    try:
        repo = find_repo_root(args.repo)
        if args.list_targets:
            print_targets(repo, args.json)
            return 0

        connection = resolve_connection(repo, args.service, args.database, args.config_source)
        if args.connection_test:
            public = connection.public_dict()
            print("Target: " + json.dumps(public, ensure_ascii=False))
            escaped_database = connection.database.replace("'", "''")
            sql = (
                "SELECT VERSION() AS mysql_version, "
                f"EXISTS(SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = '{escaped_database}') "
                "AS target_database_exists"
            )
            completed = mysql_run(connection, sql, skip_headers=False, include_database=False)
            if completed.stdout:
                print(completed.stdout, end="" if completed.stdout.endswith("\n") else "\n")
            if completed.stderr:
                print(completed.stderr, end="" if completed.stderr.endswith("\n") else "\n", file=sys.stderr)
            return completed.returncode

        sql = read_sql(args)
        if args.allow_write:
            verb, core = validate_write(sql, connection.database)
            if args.config_source == "auto" and connection.config_source.startswith("local:"):
                raise ValueError(
                    "Auto configuration fell back to the repository datasource; pass --config-source local "
                    "explicitly only after verifying that target"
                )
            if args.confirm_database != connection.database:
                raise ValueError("--confirm-database must exactly match the resolved target database")
            if args.confirm_host != connection.host:
                raise ValueError("--confirm-host must exactly match the resolved datasource host")
            if not args.reason or len(args.reason.strip()) < 8:
                raise ValueError("--reason must describe the fixture, test, or repair in at least 8 characters")
            if args.commit and not args.execute:
                raise ValueError("--commit requires --execute")
            print(f"WRITE PREVIEW: {verb} on {connection.host}:{connection.port}/{connection.database}")
            print(f"Reason: {args.reason.strip()}")
            print(core + ";")
            if not args.execute:
                print("Not executed. Add --execute to run in a transaction; add --commit to persist.")
                return 0
            end = "COMMIT" if args.commit else "ROLLBACK"
            wrapped = f"START TRANSACTION; {core}; SELECT ROW_COUNT() AS affected_rows; {end};"
            return_code = run_mysql(connection, wrapped)
            if return_code == 0:
                print("Transaction committed." if args.commit else "Transaction rolled back.")
            return return_code

        if args.execute or args.commit or args.confirm_database or args.confirm_host or args.reason:
            raise ValueError("Write-only flags require --allow-write")
        core = validate_read(sql, connection.database)
        if args.dry_run:
            print(f"READ PREVIEW on {connection.host}:{connection.port}/{connection.database}")
            print(core + ";")
            return 0
        wrapped = f"START TRANSACTION READ ONLY; {core}; ROLLBACK;"
        return run_mysql(connection, wrapped)
    except (ConfigurationError, OSError, RuntimeError, ValueError) as error:
        print(f"error: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())

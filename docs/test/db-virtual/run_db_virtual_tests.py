#!/usr/bin/env python3
"""按 db-virtual-suite.json 调用 DbQueryApi，并输出逐用例结果与汇总。

只使用 Python 标准库，避免为联调工具额外引入运行时依赖。
"""

from __future__ import annotations

import argparse
import copy
import json
import os
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Iterable, Mapping
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


DEFAULT_SUITE = Path(__file__).with_name("db-virtual-suite.json")
MISSING = object()
SUPPORTED_OPERATORS = {"equals", "exists", "length", "contains", "type"}


class SuiteValidationError(ValueError):
    """用例集不符合约定。"""


class RequestFailure(RuntimeError):
    """HTTP 请求或服务响应不符合成功约定。"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="执行 db-virtual-suite.json 中定义的 DbQueryApi 联调用例。"
    )
    parser.add_argument("--suite", type=Path, default=DEFAULT_SUITE, help="用例 JSON 路径")
    parser.add_argument(
        "--base-url",
        default=os.getenv("DB_VIRTUAL_BASE_URL"),
        help="DbQuery API 网关前缀，例如 http://localhost:8080/dbEngine；也可设 DB_VIRTUAL_BASE_URL",
    )
    parser.add_argument(
        "--token",
        default=os.getenv("DB_VIRTUAL_TOKEN"),
        help="Bearer Token；也可设 DB_VIRTUAL_TOKEN",
    )
    parser.add_argument(
        "--header",
        action="append",
        default=[],
        metavar="NAME:VALUE",
        help="额外请求头，可重复，例如 --header 'X-Tenant-Id:1'",
    )
    parser.add_argument("--case", dest="case_ids", action="append", help="只执行指定 case id，可重复")
    parser.add_argument("--timeout", type=float, default=30.0, help="单请求超时秒数，默认 30")
    parser.add_argument("--report", type=Path, help="可选：将完整汇总写入此 JSON 文件")
    parser.add_argument("--include-response", action="store_true", help="报告中保存完整成功响应")
    parser.add_argument("--dry-run", action="store_true", help="仅校验 JSON 与列出用例，不请求服务")
    return parser.parse_args()


def load_suite(path: Path) -> dict[str, Any]:
    try:
        parsed = json.loads(path.read_text(encoding="utf-8"))
    except FileNotFoundError as exc:
        raise SuiteValidationError(f"找不到用例文件: {path}") from exc
    except json.JSONDecodeError as exc:
        raise SuiteValidationError(f"用例 JSON 非法: {path}:{exc.lineno}:{exc.colno}: {exc.msg}") from exc
    if not isinstance(parsed, dict):
        raise SuiteValidationError("用例文件根节点必须是对象")
    return parsed


def validate_suite(suite: Mapping[str, Any]) -> None:
    if not isinstance(suite.get("name"), str) or not suite["name"].strip():
        raise SuiteValidationError("name 必须是非空字符串")
    api = suite.get("api")
    if not isinstance(api, Mapping) or not isinstance(api.get("successCode"), int):
        raise SuiteValidationError("api.successCode 必须是整数")
    catalog = suite.get("virtualCatalog")
    if not isinstance(catalog, Mapping):
        raise SuiteValidationError("virtualCatalog 必须是对象")
    entities = catalog.get("entities")
    if not isinstance(entities, list) or not entities:
        raise SuiteValidationError("virtualCatalog.entities 必须为非空数组")
    entity_codes = set()
    for entity in entities:
        if not isinstance(entity, Mapping) or not isinstance(entity.get("entityCode"), str):
            raise SuiteValidationError("每个 virtualCatalog.entities 元素必须包含 entityCode")
        if entity["entityCode"] in entity_codes:
            raise SuiteValidationError(f"重复 entityCode: {entity['entityCode']}")
        entity_codes.add(entity["entityCode"])
    relations = catalog.get("relations")
    if not isinstance(relations, list):
        raise SuiteValidationError("virtualCatalog.relations 必须是数组")
    for relation in relations:
        if not isinstance(relation, Mapping):
            raise SuiteValidationError("virtualCatalog.relations 元素必须是对象")
        required = ("sourceEntityCode", "targetEntityCode", "relationCode", "resultMode", "mappings")
        if any(not relation.get(field) for field in required):
            raise SuiteValidationError(f"关系定义缺少字段: {relation}")
        if relation["sourceEntityCode"] not in entity_codes or relation["targetEntityCode"] not in entity_codes:
            raise SuiteValidationError(f"关系引用了未知实体: {relation['relationCode']}")
        if relation["resultMode"] not in ("OBJECT", "COLLECTION"):
            raise SuiteValidationError(f"关系 resultMode 只支持 OBJECT/COLLECTION: {relation['relationCode']}")
        if relation.get("reverseResultMode") not in (None, "OBJECT", "COLLECTION"):
            raise SuiteValidationError(
                f"关系 reverseResultMode 只支持 OBJECT/COLLECTION: {relation['relationCode']}"
            )
    cases = suite.get("testCases")
    if not isinstance(cases, list) or not cases:
        raise SuiteValidationError("testCases 必须为非空数组")
    case_ids = set()
    for case in cases:
        if not isinstance(case, Mapping):
            raise SuiteValidationError("testCases 元素必须是对象")
        case_id = case.get("id")
        if not isinstance(case_id, str) or not case_id:
            raise SuiteValidationError("每个测试用例必须包含非空 id")
        if case_id in case_ids:
            raise SuiteValidationError(f"重复测试用例 id: {case_id}")
        case_ids.add(case_id)
        request = case.get("request")
        if not isinstance(request, Mapping) or not isinstance(request.get("path"), str):
            raise SuiteValidationError(f"测试用例 {case_id} 缺少 request.path")
        if not request["path"].startswith("/"):
            raise SuiteValidationError(f"测试用例 {case_id} 的 request.path 必须以 / 开始")
        if not isinstance(request.get("body"), Mapping):
            raise SuiteValidationError(f"测试用例 {case_id} 的 request.body 必须是对象")
        assertions = case.get("expect")
        if not isinstance(assertions, list) or not assertions:
            raise SuiteValidationError(f"测试用例 {case_id} 必须有非空 expect")
        for assertion in assertions:
            if not isinstance(assertion, Mapping):
                raise SuiteValidationError(f"测试用例 {case_id} 的 expect 元素必须是对象")
            if not isinstance(assertion.get("path"), str) or not assertion["path"]:
                raise SuiteValidationError(f"测试用例 {case_id} 的断言缺少 path")
            if assertion.get("operator") not in SUPPORTED_OPERATORS:
                raise SuiteValidationError(f"测试用例 {case_id} 的断言 operator 不支持: {assertion.get('operator')}")
            if assertion["operator"] != "exists" and "value" not in assertion:
                raise SuiteValidationError(f"测试用例 {case_id} 的断言缺少 value")


def parse_headers(raw_headers: Iterable[str], token: str | None) -> dict[str, str]:
    headers = {"Accept": "application/json", "Content-Type": "application/json;charset=UTF-8"}
    for raw in raw_headers:
        if ":" not in raw:
            raise SuiteValidationError(f"请求头格式必须为 NAME:VALUE: {raw}")
        name, value = raw.split(":", 1)
        if not name.strip():
            raise SuiteValidationError(f"请求头名称不能为空: {raw}")
        headers[name.strip()] = value.strip()
    if token and "Authorization" not in headers:
        headers["Authorization"] = f"Bearer {token}"
    return headers


def select_cases(suite: Mapping[str, Any], requested_ids: list[str] | None) -> list[Mapping[str, Any]]:
    cases = list(suite["testCases"])
    if not requested_ids:
        return cases
    by_id = {case["id"]: case for case in cases}
    missing = [case_id for case_id in requested_ids if case_id not in by_id]
    if missing:
        raise SuiteValidationError(f"未找到指定测试用例: {', '.join(missing)}")
    return [by_id[case_id] for case_id in requested_ids]


def interpolate(value: Any, variables: Mapping[str, Any]) -> Any:
    if isinstance(value, str):
        full_match = re.fullmatch(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}", value)
        if full_match:
            key = full_match.group(1)
            if key not in variables:
                raise SuiteValidationError(f"未定义变量: {key}")
            return copy.deepcopy(variables[key])

        def replace(match: re.Match[str]) -> str:
            key = match.group(1)
            if key not in variables:
                raise SuiteValidationError(f"未定义变量: {key}")
            return str(variables[key])

        return re.sub(r"\$\{([A-Za-z_][A-Za-z0-9_]*)\}", replace, value)
    if isinstance(value, list):
        return [interpolate(item, variables) for item in value]
    if isinstance(value, Mapping):
        return {key: interpolate(item, variables) for key, item in value.items()}
    return value


def response_json(status: int, body: bytes) -> Any:
    text = body.decode("utf-8", errors="replace")
    try:
        return json.loads(text)
    except json.JSONDecodeError as exc:
        preview = text[:800]
        raise RequestFailure(f"HTTP {status} 响应不是 JSON: {preview}") from exc


def execute_request(base_url: str, path: str, payload: Mapping[str, Any], headers: Mapping[str, str], timeout: float) -> tuple[int, Any]:
    url = f"{base_url.rstrip('/')}{path}"
    request = Request(
        url,
        data=json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8"),
        headers=dict(headers),
        method="POST",
    )
    try:
        with urlopen(request, timeout=timeout) as response:
            status = response.getcode()
            parsed = response_json(status, response.read())
    except HTTPError as exc:
        parsed = response_json(exc.code, exc.read())
        raise RequestFailure(f"HTTP {exc.code}: {json.dumps(parsed, ensure_ascii=False)}") from exc
    except URLError as exc:
        raise RequestFailure(f"请求失败: {exc.reason}") from exc
    if not 200 <= status < 300:
        raise RequestFailure(f"HTTP 状态异常: {status}")
    return status, parsed


def read_path(value: Any, path: str) -> Any:
    current = value
    for field, index in re.findall(r"(?:^|\.)([A-Za-z_][A-Za-z0-9_]*)|\[([0-9]+)\]", path):
        if field:
            if not isinstance(current, Mapping) or field not in current:
                return MISSING
            current = current[field]
        else:
            if not isinstance(current, list):
                return MISSING
            numeric_index = int(index)
            if numeric_index >= len(current):
                return MISSING
            current = current[numeric_index]
    return current


def values_equal(actual: Any, expected: Any) -> bool:
    if isinstance(actual, bool) != isinstance(expected, bool):
        return False
    return actual == expected


def deep_contains(actual: Any, expected: Any) -> bool:
    if isinstance(expected, Mapping):
        return isinstance(actual, Mapping) and all(
            key in actual and deep_contains(actual[key], value) for key, value in expected.items()
        )
    if isinstance(expected, list):
        return isinstance(actual, list) and all(any(deep_contains(item, wanted) for item in actual) for wanted in expected)
    return values_equal(actual, expected)


def type_name(value: Any) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "boolean"
    if isinstance(value, Mapping):
        return "object"
    if isinstance(value, list):
        return "array"
    if isinstance(value, (int, float)):
        return "number"
    return "string"


def evaluate_assertion(response: Any, assertion: Mapping[str, Any]) -> dict[str, Any]:
    path = assertion["path"]
    operator = assertion["operator"]
    actual = read_path(response, path)
    expected = assertion.get("value")
    result = {"path": path, "operator": operator, "expected": expected}

    if operator == "exists":
        passed = actual is not MISSING
    elif actual is MISSING:
        passed = False
        result["actual"] = "<missing>"
    elif operator == "equals":
        passed = values_equal(actual, expected)
    elif operator == "length":
        try:
            passed = len(actual) == expected
        except TypeError:
            passed = False
    elif operator == "contains":
        if isinstance(actual, list):
            passed = any(deep_contains(item, expected) for item in actual)
        else:
            passed = deep_contains(actual, expected)
    elif operator == "type":
        passed = type_name(actual) == expected
    else:
        raise SuiteValidationError(f"未知断言操作符: {operator}")

    if actual is not MISSING:
        result["actual"] = actual
    result["passed"] = passed
    if not passed:
        raise AssertionError(
            f"断言失败 path={path}, operator={operator}, expected={expected!r}, actual={result.get('actual')!r}"
        )
    return result


def ensure_success_envelope(response: Any, success_code: int) -> None:
    if not isinstance(response, Mapping):
        raise RequestFailure("响应根节点必须是 R<T> 对象")
    if response.get("code") != success_code:
        raise RequestFailure(
            f"服务返回失败 code={response.get('code')!r}, msg={response.get('msg')!r}"
        )


def run_case(
    case: Mapping[str, Any],
    suite: Mapping[str, Any],
    base_url: str,
    headers: Mapping[str, str],
    timeout: float,
    include_response: bool,
) -> dict[str, Any]:
    started = time.perf_counter()
    result: dict[str, Any] = {"id": case["id"], "description": case.get("description", ""), "passed": False}
    try:
        variables = suite.get("variables", {})
        body = interpolate(case["request"]["body"], variables)
        status, response = execute_request(base_url, case["request"]["path"], body, headers, timeout)
        result["httpStatus"] = status
        ensure_success_envelope(response, suite["api"]["successCode"])
        assertions = []
        for assertion in case["expect"]:
            assertions.append(evaluate_assertion(response, assertion))
        result["assertions"] = assertions
        if include_response:
            result["response"] = response
        result["passed"] = True
    except (AssertionError, RequestFailure, SuiteValidationError) as exc:
        result["error"] = str(exc)
    finally:
        result["durationMs"] = round((time.perf_counter() - started) * 1000, 2)
    return result


def now_iso() -> str:
    return datetime.now(timezone.utc).astimezone().isoformat(timespec="seconds")


def emit_report(report: Mapping[str, Any], path: Path | None) -> None:
    summary = report["summary"]
    print(f"\nDb Virtual test suite: {report['suite']}")
    print(f"Result: {summary['passed']}/{summary['total']} passed, {summary['failed']} failed")
    for item in report["results"]:
        marker = "PASS" if item["passed"] else "FAIL"
        suffix = "" if item["passed"] else f" - {item.get('error', 'unknown error')}"
        print(f"[{marker}] {item['id']} ({item['durationMs']} ms){suffix}")
    if path:
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"Report: {path}")


def main() -> int:
    args = parse_args()
    suite = load_suite(args.suite)
    validate_suite(suite)
    cases = select_cases(suite, args.case_ids)
    started_at = now_iso()

    if args.dry_run:
        results = [
            {"id": case["id"], "description": case.get("description", ""), "passed": True, "durationMs": 0.0}
            for case in cases
        ]
        report = {
            "suite": suite["name"],
            "dryRun": True,
            "startedAt": started_at,
            "finishedAt": now_iso(),
            "summary": {"total": len(results), "passed": len(results), "failed": 0},
            "results": results,
        }
        emit_report(report, args.report)
        return 0

    if not args.base_url:
        raise SuiteValidationError("缺少 --base-url 或 DB_VIRTUAL_BASE_URL；可参考 suite.api.baseUrlExample")
    if args.timeout <= 0:
        raise SuiteValidationError("--timeout 必须大于 0")
    headers = parse_headers(args.header, args.token)
    results = [
        run_case(case, suite, args.base_url, headers, args.timeout, args.include_response)
        for case in cases
    ]
    passed = sum(1 for result in results if result["passed"])
    report = {
        "suite": suite["name"],
        "dryRun": False,
        "startedAt": started_at,
        "finishedAt": now_iso(),
        "summary": {"total": len(results), "passed": passed, "failed": len(results) - passed},
        "results": results,
    }
    emit_report(report, args.report)
    return 0 if report["summary"]["failed"] == 0 else 1


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except SuiteValidationError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        raise SystemExit(2)

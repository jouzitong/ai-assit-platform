from __future__ import annotations

from copy import deepcopy
from functools import lru_cache
import json
from pathlib import Path
import re
from typing import Any, Literal

from agents import function_tool
from pydantic import BaseModel, ConfigDict, Field

from .render_validation import validate_render_document


RENDER_JSON_VALIDATE_TOOL_CODE = "render_json_validate_tool"
COMPONENT_TEST_CASES = {
    "list-table",
    "form-edit",
    "line-chart",
    "combo-chart",
    "radar-chart",
}
_IDENTIFIER_PATTERN = r"^[A-Za-z][A-Za-z0-9_.-]{0,127}$"

JsonScalar = str | int | float | bool


class DatasourceFilterInput(BaseModel):
    """The deliberately small, strict filter surface exposed to the model."""

    model_config = ConfigDict(extra="forbid")

    field: str = Field(pattern=_IDENTIFIER_PATTERN)
    operator: Literal[
        "eq",
        "ne",
        "gt",
        "gte",
        "lt",
        "lte",
        "like",
        "starts_with",
        "ends_with",
        "in",
        "not_in",
        "is_null",
        "is_not_null",
    ]
    value: JsonScalar | list[JsonScalar] | None = None


class DatasourceSortInput(BaseModel):
    model_config = ConfigDict(extra="forbid")

    field: str = Field(pattern=_IDENTIFIER_PATTERN)
    direction: Literal["asc", "desc"]


class RenderDatasourceInput(BaseModel):
    """Only datasource facts may cross the model/tool boundary."""

    model_config = ConfigDict(extra="forbid")

    key: str = Field(pattern=_IDENTIFIER_PATTERN)
    model: str = Field(pattern=_IDENTIFIER_PATTERN)
    fields: list[str] = Field(min_length=1, max_length=100)
    filters: list[DatasourceFilterInput]
    sorts: list[DatasourceSortInput]
    page: int = Field(ge=1)
    page_size: int = Field(ge=1, le=100)


ComponentTestCase = Literal[
    "list-table",
    "form-edit",
    "line-chart",
    "combo-chart",
    "radar-chart",
]


def validate_render_json_for_run(run: dict[str, Any], render_document: Any) -> dict[str, Any]:
    """Validate an already materialized document for unit tests and callers.

    The public Agent tool intentionally does not expose this unconstrained value.
    Keeping the validator as a normal Python function preserves the deterministic
    security gate without asking the model to serialize a large JSON document.
    """

    analysis = validate_render_document(render_document)
    result = {
        "tool": RENDER_JSON_VALIDATE_TOOL_CODE,
        **analysis,
    }
    if analysis.get("valid") and isinstance(render_document, dict):
        result["renderDocument"] = deepcopy(render_document)
    return result


def generate_render_json_for_run(
    run: dict[str, Any],
    component_test_case: str,
    datasource: RenderDatasourceInput | dict[str, Any],
) -> dict[str, Any]:
    """Materialize one frozen component case and validate its final document."""

    try:
        if component_test_case not in COMPONENT_TEST_CASES:
            raise ValueError(f"Unknown component test case: {component_test_case}")
        source = (
            datasource
            if isinstance(datasource, RenderDatasourceInput)
            else RenderDatasourceInput.model_validate(datasource)
        )
        document = _materialize_case(component_test_case, source)
    except (TypeError, ValueError, KeyError, json.JSONDecodeError) as exc:
        return {
            "tool": RENDER_JSON_VALIDATE_TOOL_CODE,
            "valid": False,
            "errors": [{
                "code": "DATASOURCE_CONFIGURATION_INVALID",
                "message": str(exc),
                "jsonPath": "$.datasource",
                "recoverable": True,
                "severity": "ERROR",
            }],
            "warnings": [],
            "componentTestCase": component_test_case,
        }

    analysis = validate_render_document(document)
    result = {
        "tool": RENDER_JSON_VALIDATE_TOOL_CODE,
        "componentTestCase": component_test_case,
        **analysis,
    }
    if analysis.get("valid"):
        # This exact object becomes the authoritative render-document artifact.
        result["renderDocument"] = document
    return result


def build_render_json_validate_tool(run: dict[str, Any], function_tool_factory: Any) -> Any:
    """Build a strict-schema fixture materializer and Render JSON validator."""

    def validate_render_json(
        component_test_case: ComponentTestCase,
        datasource: RenderDatasourceInput,
    ) -> dict[str, Any]:
        """Materialize a frozen component test case from datasource facts only."""

        return generate_render_json_for_run(run, component_test_case, datasource)

    decorator = function_tool_factory(
        name_override=RENDER_JSON_VALIDATE_TOOL_CODE,
        description_override=(
            "Materialize and deterministically validate a Render JSON document from one approved component test case. "
            "Pass only the successful preview's datasource configuration; never pass a hand-written RenderDocument. "
            "The tool derives list columns and chart bindings from datasource.fields."
        ),
    )
    return decorator(validate_render_json)


@function_tool
def render_json_validate_tool(
    component_test_case: ComponentTestCase,
    datasource: RenderDatasourceInput,
) -> dict[str, Any]:
    """Materialize and validate a frozen Render JSON test case."""

    return generate_render_json_for_run({}, component_test_case, datasource)


@lru_cache(maxsize=len(COMPONENT_TEST_CASES))
def _load_case(component_test_case: str) -> dict[str, Any]:
    if component_test_case not in COMPONENT_TEST_CASES:
        raise ValueError(f"Unknown component test case: {component_test_case}")
    path = (
        Path(__file__).resolve().parents[1]
        / "skills"
        / "render-json-generation"
        / "assets"
        / "component-test-cases"
        / f"{component_test_case}.json"
    )
    if not path.is_file():
        raise ValueError(f"Component test case resource is missing: {component_test_case}")
    payload = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(payload, dict) or payload.get("caseId") != component_test_case:
        raise ValueError(f"Component test case resource is invalid: {component_test_case}")
    document = payload.get("document")
    if not isinstance(document, dict):
        raise ValueError(f"Component test case has no document: {component_test_case}")
    return payload


def _materialize_case(
    component_test_case: str,
    datasource: RenderDatasourceInput,
) -> dict[str, Any]:
    case = _load_case(component_test_case)
    minimum_fields = int(case.get("minimumFields") or 1)
    fields = _validated_fields(datasource.fields)
    if len(fields) < minimum_fields:
        raise ValueError(
            f"{component_test_case} requires at least {minimum_fields} datasource field(s)"
        )
    if component_test_case == "radar-chart" and len(fields) > 8:
        raise ValueError("radar-chart supports at most 8 datasource fields")

    document = deepcopy(case["document"])
    root = document["root"]
    root["datasource"] = _materialize_datasource(datasource)

    if component_test_case == "list-table":
        schema = _required_schema(root)
        schema["fields"] = [
            {"key": field, "name": field, "label": field, "field": [field]}
            for field in fields
        ]
    elif component_test_case == "form-edit":
        schema = _required_schema(root)
        schema["fields"] = [
            {
                "key": field,
                "name": field,
                "label": field,
                "type": "text",
                "options": {"span": 6, "labelPosition": "left"},
            }
            for field in fields
        ]
    elif component_test_case == "line-chart":
        _materialize_line_bindings(root, fields)
    elif component_test_case == "combo-chart":
        _materialize_combo_bindings(root, fields)
    elif component_test_case == "radar-chart":
        _materialize_radar_bindings(root, fields)
    return document


def _materialize_datasource(source: RenderDatasourceInput) -> dict[str, Any]:
    fields = _validated_fields(source.fields)
    datasource: dict[str, Any] = {
        "key": source.key,
        "type": "db-query-list",
        "model": source.model,
        "page": source.page,
        "page_size": source.page_size,
        "ext": {"fields": fields},
    }
    if source.filters:
        filter_dict: dict[str, Any] = {}
        for item in source.filters:
            if item.field not in fields:
                raise ValueError(f"Filter field is not in datasource.fields: {item.field}")
            if item.operator in {"is_null", "is_not_null"}:
                filter_dict[item.field] = {"op": item.operator}
            elif item.operator in {"eq"}:
                filter_dict[item.field] = item.value
            else:
                filter_dict[item.field] = {"op": item.operator, "value": item.value}
        # Leave filterExpr unset: the legacy DB-query contract treats a
        # filter_dict without an expression as an implicit AND and rejects a
        # simultaneous filterExpr/filter_dict pair.
        datasource["filter_dict"] = filter_dict
    if source.sorts:
        for item in source.sorts:
            if item.field not in fields:
                raise ValueError(f"Sort field is not in datasource.fields: {item.field}")
        datasource["ext"]["sorts"] = [
            {"field": item.field, "order": item.direction}
            for item in source.sorts
        ]
    return datasource


def _materialize_line_bindings(root: dict[str, Any], fields: list[str]) -> None:
    root["bindings"] = {
        "category": {"source": fields[0]},
        "series": [{"source": field} for field in fields[1:]],
    }


def _materialize_combo_bindings(root: dict[str, Any], fields: list[str]) -> None:
    root["bindings"] = {
        "category": {"source": fields[0]},
        "barSeries": [{"source": fields[1]}],
        "lineSeries": [{"source": field} for field in fields[2:]],
    }


def _materialize_radar_bindings(root: dict[str, Any], fields: list[str]) -> None:
    root["bindings"] = {
        "indicators": [{"source": field} for field in fields],
    }


def _required_schema(root: dict[str, Any]) -> dict[str, Any]:
    props = root.get("props")
    if not isinstance(props, dict) or not isinstance(props.get("schema"), dict):
        raise ValueError("Component test case must provide props.schema")
    return props["schema"]


def _validated_fields(value: list[str]) -> list[str]:
    fields: list[str] = []
    seen: set[str] = set()
    for field in value:
        if not isinstance(field, str) or not re.fullmatch(_IDENTIFIER_PATTERN, field):
            raise ValueError(f"Invalid datasource field: {field}")
        if field in seen:
            raise ValueError(f"Duplicate datasource field: {field}")
        seen.add(field)
        fields.append(field)
    return fields

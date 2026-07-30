import json
import unittest
from pathlib import Path
from types import SimpleNamespace

from agent_provider.artifacts import (
    RunArtifactCollector,
    merge_authoritative_artifacts,
    render_document_hash,
)
from agent_provider.events import EventEmitter, emit_sdk_event, map_run_item_event
from agent_provider.runtime import extract_artifacts, extract_usage
from agent_provider.runtime import runner


ARTIFACT_FIXTURE = Path(__file__).resolve().parents[1] / "fixtures" / "agent-runtime-artifact-result.json"


class EventsAndUsageTest(unittest.TestCase):
    def test_emits_platform_event_envelope(self) -> None:
        frames = []
        emitter = EventEmitter(
            {
                "run": {
                    "runId": "run-1",
                    "requestId": "request-1",
                    "traceId": "trace-1",
                }
            },
            frames.append,
        )

        emitter.event("assistant.message.delta", status="RUNNING", delta="hello")

        self.assertEqual("event", frames[0]["type"])
        self.assertEqual("2.0", frames[0]["protocolVersion"])
        self.assertEqual("assistant.message.delta", frames[0]["eventType"])
        self.assertEqual("run-1", frames[0]["runId"])

    def test_maps_sdk_tool_failure_without_exposing_raw_item(self) -> None:
        raw = SimpleNamespace(name="validator", call_id="call-1", arguments='{"x":1}')
        item = SimpleNamespace(raw_item=raw, output={"success": False, "error": "bad"})
        mapped = map_run_item_event(SimpleNamespace(name="tool_output", item=item))

        self.assertEqual("tool.failed", mapped[0])
        self.assertEqual("FAILED", mapped[1])
        self.assertEqual("工具调用失败", mapped[2])
        self.assertEqual("call-1", mapped[3]["callId"])

    def test_maps_gateway_sdk_names_back_to_versioned_platform_tool_identity(self) -> None:
        raw = SimpleNamespace(name="gateway_issue_create_v4", call_id="call-2")
        item = SimpleNamespace(raw_item=raw, output={"status": "SUCCESS"})
        mapped = map_run_item_event(
            SimpleNamespace(name="tool_output", item=item),
            lambda name: {
                "code": "issue-create",
                "version": 4,
            } if name == "gateway_issue_create_v4" else None,
        )

        self.assertEqual("issue-create", mapped[3]["toolCode"])
        self.assertEqual(4, mapped[3]["toolVersion"])

    def test_tool_lifecycle_keeps_one_code_and_real_summaries(self) -> None:
        started_item = SimpleNamespace(
            raw_item=SimpleNamespace(name="validator", call_id="call-3", arguments='{"value":1}'),
        )
        completed_item = SimpleNamespace(
            raw_item=SimpleNamespace(type="function_call_output", call_id="call-3"),
            output={"success": True, "count": 3},
        )

        started = map_run_item_event(SimpleNamespace(name="tool_called", item=started_item))
        completed = map_run_item_event(SimpleNamespace(name="tool_output", item=completed_item))

        self.assertEqual("call-3", started[3]["activityCode"])
        self.assertEqual("call-3", completed[3]["activityCode"])
        self.assertEqual('{"value":1}', started[3]["inputSummary"])
        self.assertEqual('{"success": true, "count": 3}', completed[3]["outputSummary"])

    def test_mapped_event_observer_receives_raw_tool_output_for_evidence_collection(self) -> None:
        observed: list[tuple[str, dict[str, object], object]] = []
        item = SimpleNamespace(
            raw_item=SimpleNamespace(
                name="knowledge_base_search_tool",
                call_id="call-kb",
            ),
            output={
                "success": True,
                "kbCode": "db-schema",
                "items": [{"documentId": "doc-1", "content": "user 表字段：id bigint"}],
            },
        )
        frames: list[dict[str, object]] = []
        emitter = EventEmitter({"run": {"runId": "run-1"}}, frames.append)

        emit_sdk_event(
            SimpleNamespace(type="run_item_stream_event", name="tool_output", item=item),
            emitter,
            lambda agent: None,
            mapped_event_observer=lambda event_type, ext, raw_item: observed.append(
                (event_type, ext, raw_item)
            ),
        )

        self.assertEqual("tool.completed", observed[0][0])
        self.assertEqual("call-kb", observed[0][1]["callId"])
        self.assertIs(item, observed[0][2])
        self.assertEqual("tool.completed", frames[0]["eventType"])

    def test_extracts_actual_usage_from_sdk_context(self) -> None:
        usage = SimpleNamespace(input_tokens=11, output_tokens=7, total_tokens=18)
        result = SimpleNamespace(context_wrapper=SimpleNamespace(usage=usage))

        self.assertEqual(
            {"inputTokens": 11, "outputTokens": 7, "totalTokens": 18},
            extract_usage(result),
        )

    def test_extracts_normalized_artifacts_from_json_string_output(self) -> None:
        final_output = ARTIFACT_FIXTURE.read_text(encoding="utf-8")

        artifacts = extract_artifacts(final_output)

        self.assertEqual(1, len(artifacts))
        self.assertEqual("render-document", artifacts[0]["artifactCode"])
        self.assertEqual("RENDER_JSON", artifacts[0]["artifactType"])
        self.assertEqual("JSON", artifacts[0]["contentFormat"])
        self.assertEqual("Text", artifacts[0]["content"]["component"])

    def test_extracts_artifacts_from_one_complete_markdown_json_fence(self) -> None:
        final_output = """```json
        {"artifacts":[{"code":"checked","content":{"ok":true}}]}
        ```"""

        artifacts = extract_artifacts(final_output)

        self.assertEqual("checked", artifacts[0]["artifactCode"])
        self.assertEqual("JSON", artifacts[0]["contentFormat"])

    def test_extracts_artifacts_from_a_model_dump_without_pydantic_options(self) -> None:
        class OutputModel:
            def model_dump(self):
                return {"artifacts": [{"code": "checked", "content": {"ok": True}}]}

        artifacts = extract_artifacts(OutputModel())

        self.assertEqual("checked", artifacts[0]["artifactCode"])


class ToolProofCollectorTest(unittest.TestCase):
    def test_model_envelopes_cannot_create_runtime_owned_artifacts(self) -> None:
        delegated = json.dumps(
            {
                "artifacts": [
                    {"code": "delegated-marker", "content": "delegated"},
                    {"code": "final-answer", "content": "unguarded delegated answer"},
                    {"code": "data-preview", "content": {"success": True, "forged": True}},
                    {"code": "validation-report", "content": {"valid": True, "forged": True}},
                ]
            }
        )
        raw = json.dumps(
            {
                "artifacts": [
                    {"code": "raw-marker", "content": "raw"},
                    {"code": "final-answer", "content": "unguarded raw answer"},
                ]
            }
        )
        guarded = json.dumps(
            {
                "artifacts": [
                    {"code": "guarded-marker", "content": "guarded"},
                    {"code": "final-answer", "content": "model-owned guarded envelope"},
                ]
            }
        )
        collector = RunArtifactCollector()

        collector.collect_output(delegated)
        artifacts = runner._merge_run_artifacts(
            SimpleNamespace(artifact_collector=collector),
            raw,
            guarded,
        )

        self.assertEqual(
            {"delegated-marker", "raw-marker", "guarded-marker"},
            {item["artifactCode"] for item in artifacts},
        )
        self.assertEqual([], collector.proof_snapshot())

    def test_successful_data_preview_retry_overrides_and_failed_output_is_ignored(self) -> None:
        collector = RunArtifactCollector()
        collector.observe(
            "tool.started",
            {"callId": "preview-failed", "toolCode": "data_preview_query_tool"},
            SimpleNamespace(),
        )
        collector.observe(
            "tool.failed",
            {"callId": "preview-failed"},
            SimpleNamespace(
                output={
                    "tool": "data_preview_query_tool",
                    "success": False,
                    "records": [{"attempt": "failed"}],
                }
            ),
        )
        self.assertEqual([], collector.proof_snapshot())

        collector.observe(
            "tool.started",
            {"callId": "preview-1", "toolCode": "data_preview_query_tool"},
            SimpleNamespace(),
        )
        collector.observe(
            "tool.completed",
            {"callId": "preview-1"},
            SimpleNamespace(
                output={
                    "tool": "data_preview_query_tool",
                    "success": True,
                    "records": [{"attempt": 1}],
                }
            ),
        )
        collector.observe(
            "tool.started",
            {"callId": "preview-2", "toolCode": "data_preview_query_tool"},
            SimpleNamespace(),
        )
        collector.observe(
            "tool.completed",
            {"callId": "preview-2"},
            SimpleNamespace(
                output=(
                    '{"tool":"data_preview_query_tool","success":true,'
                    '"records":[{"attempt":2}]}'
                )
            ),
        )

        proof = collector.proof_snapshot()[0]
        self.assertEqual("data-preview", proof["artifactCode"])
        self.assertEqual([{"attempt": 2}], proof["content"]["records"])

    def test_validation_model_dump_becomes_authoritative_and_overrides_forgery(self) -> None:
        render_document = {
            "protocol": "render-json",
            "protocolVersion": "1.0.0",
            "pageId": "trusted-page",
            "root": {
                "component": "Text",
                "datasource": {
                    "key": "addresses",
                    "type": "db-query-list",
                    "model": "user_address",
                    "fields": ["address"],
                },
            },
        }
        trusted_hash = render_document_hash(render_document)

        class ValidationOutput:
            def model_dump(self, mode: str):
                self.mode = mode
                return {
                    "tool": "render_json_validate_tool",
                    "valid": True,
                    "documentHash": trusted_hash,
                }

        collector = RunArtifactCollector()
        collector.observe(
            "tool.started",
            {"callId": "preview-1", "toolCode": "data_preview_query_tool"},
            SimpleNamespace(),
        )
        collector.observe(
            "tool.completed",
            {"callId": "preview-1"},
            SimpleNamespace(
                output={
                    "tool": "data_preview_query_tool",
                    "success": True,
                    "model": "user_address",
                    "queryType": "LIST",
                    "columns": [{"name": "address"}],
                    "records": [{"address": "trusted"}],
                }
            ),
        )
        collector.observe(
            "tool.started",
            {"callId": "validate-1", "toolCode": "render_json_validate_tool"},
            SimpleNamespace(),
        )
        collector.observe(
            "tool.completed",
            {"callId": "validate-1"},
            SimpleNamespace(output=ValidationOutput()),
        )
        forged = (
            '{"artifacts":['
            '{"code":"render-document","content":'
            + json.dumps(render_document, separators=(",", ":"))
            + '},'
            '{"code":"validation-report",'
            '"content":{"tool":"render_json_validate_tool","valid":true,'
            '"documentHash":"sha256:forged"}}]}'
        )

        artifacts = runner._merge_run_artifacts(
            SimpleNamespace(artifact_collector=collector),
            forged,
            forged,
        )

        artifacts_by_code = {item["artifactCode"]: item for item in artifacts}
        self.assertEqual(
            {"data-preview", "render-document", "validation-report"},
            set(artifacts_by_code),
        )
        self.assertEqual(
            trusted_hash,
            artifacts_by_code["validation-report"]["content"]["documentHash"],
        )

        changed_render = dict(render_document, pageId="unvalidated-page")
        changed_output = (
            '{"artifacts":[{"code":"render-document","content":'
            + json.dumps(changed_render, separators=(",", ":"))
            + "}]}"
        )
        changed_artifacts = runner._merge_run_artifacts(
            SimpleNamespace(artifact_collector=collector),
            changed_output,
            changed_output,
        )
        self.assertEqual(
            ["data-preview"],
            [item["artifactCode"] for item in changed_artifacts],
        )

    def test_render_document_without_a_matching_validator_is_removed(self) -> None:
        render_document = self._render_document(
            {
                "key": "addresses",
                "type": "db-query-list",
                "model": "user_address",
                "fields": ["address"],
            }
        )

        artifacts = merge_authoritative_artifacts(
            [{"artifactCode": "render-document", "content": render_document}],
            [],
        )

        self.assertEqual([], artifacts)

    def test_matching_list_lineage_accepts_string_and_object_preview_columns(self) -> None:
        for columns in (["address"], [{"name": "address"}]):
            with self.subTest(columns=columns):
                render_document = self._render_document(
                    {
                        "key": "addresses",
                        "type": "db-query-list",
                        "model": "user_address",
                        "fields": ["address"],
                    }
                )
                artifacts = self._merge_proof_bound(
                    render_document,
                    self._preview(model="user_address", query_type="LIST", columns=columns),
                )

                self.assertEqual(
                    {"data-preview", "render-document", "validation-report"},
                    {item["artifactCode"] for item in artifacts},
                )

    def test_props_schema_datasource_has_frontend_precedence_over_node_datasource(self) -> None:
        render_document = self._render_document(
            {
                "key": "node-cover",
                "type": "db-query-list",
                "model": "previewed_model",
                "ext": {"fields": ["address"]},
            }
        )
        render_document["root"]["component"] = "zg-list-main-layout"
        render_document["root"]["props"] = {
            "schema": {
                "datasource": {
                    "key": "effective-source",
                    "type": "db-query-list",
                    "model": "unpreviewed_model",
                    "ext": {"fields": ["address"]},
                },
                "fields": [
                    {"key": "address", "field": ["address"]},
                ],
            }
        }

        artifacts = self._merge_proof_bound(
            render_document,
            self._preview(
                model="previewed_model",
                query_type="LIST",
                columns=["address"],
            ),
        )

        self.assertEqual([], artifacts)

    def test_props_schema_datasource_fields_cannot_hide_behind_node_datasource(self) -> None:
        render_document = self._render_document(
            {
                "key": "node-cover",
                "type": "db-query-list",
                "model": "user_address",
                "ext": {"fields": ["address"]},
            }
        )
        render_document["root"]["component"] = "zg-list-main-layout"
        render_document["root"]["props"] = {
            "schema": {
                "datasource": {
                    "key": "effective-source",
                    "type": "db-query-list",
                    "model": "user_address",
                    "ext": {"fields": ["address", "secret_value"]},
                },
                "fields": [{"key": "address", "field": ["address"]}],
            }
        }

        artifacts = self._merge_proof_bound(
            render_document,
            self._preview(
                model="user_address",
                query_type="LIST",
                columns=["address"],
            ),
        )

        self.assertEqual([], artifacts)

    def test_matching_props_schema_datasource_ignores_unused_node_fallback(self) -> None:
        render_document = self._render_document(
            {
                "key": "unused-node-source",
                "type": "static",
                "model": "wrong_model",
                "fields": ["secret_value"],
                "data": [],
            }
        )
        render_document["root"]["component"] = "zg-list-main-layout"
        render_document["root"]["props"] = {
            "schema": {
                "datasource": {
                    "key": "effective-source",
                    "type": "db-query-list",
                    "model": "user_address",
                    "ext": {"fields": ["address"]},
                },
                "fields": [
                    {"key": "address", "field": ["address"]},
                ],
            }
        }

        artifacts = self._merge_proof_bound(
            render_document,
            self._preview(
                model="user_address",
                query_type="LIST",
                columns=["address"],
            ),
        )

        self.assertEqual(
            {"data-preview", "render-document", "validation-report"},
            {item["artifactCode"] for item in artifacts},
        )

    def test_props_schema_fields_are_included_in_frontend_request_lineage(self) -> None:
        render_document = self._render_document(
            {
                "key": "addresses",
                "type": "db-query-list",
                "model": "user_address",
                "ext": {"fields": ["address"]},
            }
        )
        render_document["root"]["component"] = "zg-list-main-layout"
        render_document["root"]["props"] = {
            "schema": {
                "fields": [
                    {"key": "address", "field": ["address"]},
                    {"key": "secret", "field": ["profile", "secret_value"]},
                ],
            }
        }

        artifacts = self._merge_proof_bound(
            render_document,
            self._preview(
                model="user_address",
                query_type="LIST",
                columns=["address"],
            ),
        )

        self.assertEqual([], artifacts)

    def test_record_keys_can_prove_datasource_fields(self) -> None:
        render_document = self._render_document(
            {
                "key": "addresses",
                "type": "db-query-list",
                "model": "user_address",
                "ext": {"fields": ["address"]},
            }
        )
        artifacts = self._merge_proof_bound(
            render_document,
            self._preview(
                model="user_address",
                query_type="LIST",
                columns=[],
                records=[{"address": "trusted"}],
            ),
        )

        self.assertEqual(
            {"data-preview", "render-document", "validation-report"},
            {item["artifactCode"] for item in artifacts},
        )

    def test_matching_aggregate_lineage_checks_every_query_field(self) -> None:
        render_document = self._render_document(
            {
                "key": "sales",
                "type": "semantic-query",
                "model": "sales_order",
                "queryType": "aggregate",
                "fields": ["region", "amount", "status", "paid_at"],
                "dimensions": ["region"],
                "measures": [{"field": "amount", "aggregation": "sum"}],
                "filters": [{"field": "status", "operator": "eq", "value": "PAID"}],
                "sorts": [{"field": "amount", "direction": "DESC"}],
                "timeRange": {"field": "paid_at", "preset": "LAST_6_MONTHS"},
            }
        )
        preview = self._preview(
            model="sales_order",
            query_type="AGGREGATE",
            columns=[
                {"key": "region", "field": "region"},
                {"key": "sum_amount", "field": "amount"},
                {"field": "status"},
                {"field": "paid_at"},
            ],
        )

        artifacts = self._merge_proof_bound(render_document, preview)

        self.assertEqual(
            {"data-preview", "render-document", "validation-report"},
            {item["artifactCode"] for item in artifacts},
        )

    def test_count_datasource_maps_to_aggregate_preview(self) -> None:
        render_document = self._render_document(
            {
                "key": "count",
                "type": "semantic-query",
                "model": "user_address",
                "queryType": "count",
                "fields": ["id"],
                "measures": [{"field": "id", "aggregation": "count"}],
            }
        )

        artifacts = self._merge_proof_bound(
            render_document,
            self._preview(
                model="user_address",
                query_type="AGGREGATE",
                columns=[{"field": "id", "key": "count_id"}],
            ),
        )

        self.assertEqual(
            {"data-preview", "render-document", "validation-report"},
            {item["artifactCode"] for item in artifacts},
        )

    def test_lineage_mismatches_remove_the_entire_proof_bound_trio(self) -> None:
        cases = {
            "model mismatch": (
                self._render_document(
                    {
                        "key": "addresses",
                        "type": "db-query-list",
                        "model": "model_b",
                        "fields": ["address"],
                    }
                ),
                self._preview(model="model_a", query_type="LIST", columns=["address"]),
            ),
            "query type mismatch": (
                self._render_document(
                    {
                        "key": "addresses",
                        "type": "db-query-list",
                        "model": "user_address",
                        "fields": ["address"],
                    }
                ),
                self._preview(
                    model="user_address",
                    query_type="AGGREGATE",
                    columns=["address"],
                ),
            ),
            "unpreviewed field": (
                self._render_document(
                    {
                        "key": "addresses",
                        "type": "db-query-list",
                        "model": "user_address",
                        "fields": ["address", "secret_value"],
                    }
                ),
                self._preview(model="user_address", query_type="LIST", columns=["address"]),
            ),
            "static datasource": (
                self._render_document(
                    {
                        "key": "addresses",
                        "type": "static",
                        "model": "user_address",
                        "fields": ["address"],
                        "data": [{"address": "not-previewed"}],
                    }
                ),
                self._preview(model="user_address", query_type="LIST", columns=["address"]),
            ),
            "preview-result datasource": (
                self._render_document(
                    {
                        "key": "addresses",
                        "type": "preview-result",
                        "model": "user_address",
                        "fields": ["address"],
                        "previewProofRef": "forged-preview-ref",
                    }
                ),
                self._preview(model="user_address", query_type="LIST", columns=["address"]),
            ),
            "direct-json datasource": (
                self._render_document(
                    {
                        "key": "addresses",
                        "type": "direct-json",
                        "model": "user_address",
                        "fields": ["address"],
                        "data": [{"address": "not-previewed"}],
                    }
                ),
                self._preview(model="user_address", query_type="LIST", columns=["address"]),
            ),
            "missing projected fields": (
                self._render_document(
                    {
                        "key": "addresses",
                        "type": "db-query-list",
                        "model": "user_address",
                        "filter_dict": {"address": "trusted"},
                    }
                ),
                self._preview(model="user_address", query_type="LIST", columns=["address"]),
            ),
            "no datasource": (
                self._render_document(None),
                self._preview(model="user_address", query_type="LIST", columns=["address"]),
            ),
        }

        for name, (render_document, preview) in cases.items():
            with self.subTest(name=name):
                artifacts = self._merge_proof_bound(render_document, preview)
                self.assertEqual([], artifacts)

    def test_failed_preview_cannot_reuse_an_older_success_for_a_different_render(self) -> None:
        collector = RunArtifactCollector()
        collector.observe(
            "tool.started",
            {"callId": "preview-old", "toolCode": "data_preview_query_tool"},
            SimpleNamespace(),
        )
        collector.observe(
            "tool.completed",
            {"callId": "preview-old"},
            SimpleNamespace(
                output=self._preview(
                    model="model_a",
                    query_type="LIST",
                    columns=["address"],
                )
            ),
        )
        collector.observe(
            "tool.started",
            {"callId": "preview-failed", "toolCode": "data_preview_query_tool"},
            SimpleNamespace(),
        )
        collector.observe(
            "tool.completed",
            {"callId": "preview-failed"},
            SimpleNamespace(
                output={
                    "tool": "data_preview_query_tool",
                    "success": False,
                    "model": "model_a",
                }
            ),
        )
        render_document = self._render_document(
            {
                "key": "addresses",
                "type": "db-query-list",
                "model": "model_a",
                "fields": ["address"],
            }
        )
        collector.observe(
            "tool.started",
            {"callId": "validate-new", "toolCode": "render_json_validate_tool"},
            SimpleNamespace(),
        )
        collector.observe(
            "tool.completed",
            {"callId": "validate-new"},
            SimpleNamespace(
                output={
                    "tool": "render_json_validate_tool",
                    "valid": True,
                    "documentHash": render_document_hash(render_document),
                }
            ),
        )
        output = json.dumps(
            {"artifacts": [{"code": "render-document", "content": render_document}]}
        )

        artifacts = runner._merge_run_artifacts(
            SimpleNamespace(artifact_collector=collector),
            output,
            output,
        )

        self.assertEqual([], artifacts)

    @staticmethod
    def _render_document(datasource: dict[str, object] | None) -> dict[str, object]:
        root: dict[str, object] = {"component": "Text"}
        if datasource is not None:
            root["datasource"] = datasource
        return {
            "protocol": "render-json",
            "protocolVersion": "1.0.0",
            "pageId": "proof-bound-page",
            "root": root,
        }

    @staticmethod
    def _preview(
        *,
        model: str,
        query_type: str,
        columns: list[object],
        records: list[dict[str, object]] | None = None,
    ) -> dict[str, object]:
        return {
            "tool": "data_preview_query_tool",
            "success": True,
            "model": model,
            "queryType": query_type,
            "columns": columns,
            "records": records or [],
        }

    @staticmethod
    def _merge_proof_bound(
        render_document: dict[str, object],
        preview: dict[str, object],
    ) -> list[dict[str, object]]:
        validation = {
            "tool": "render_json_validate_tool",
            "valid": True,
            "documentHash": render_document_hash(render_document),
        }
        return merge_authoritative_artifacts(
            [
                {
                    "artifactCode": "render-document",
                    "artifactType": "RENDER_JSON",
                    "contentFormat": "JSON",
                    "content": render_document,
                }
            ],
            [
                {
                    "artifactCode": "data-preview",
                    "artifactType": "JSON",
                    "contentFormat": "JSON",
                    "content": preview,
                },
                {
                    "artifactCode": "validation-report",
                    "artifactType": "JSON",
                    "contentFormat": "JSON",
                    "content": validation,
                },
            ],
        )


if __name__ == "__main__":
    unittest.main()

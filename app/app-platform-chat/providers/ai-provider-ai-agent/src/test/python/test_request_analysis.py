import asyncio
import json
import sys
import unittest
from dataclasses import replace
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from agent_provider.artifacts import RunArtifactCollector, render_document_hash
from agent_provider.events import EventEmitter
from agent_provider.runtime import runner
from agent_provider.runtime.request_analysis import (
    RequestConfidenceDraft,
    RequestAnalysisDraft,
    _analysis_input,
    _extract_usage,
    _validate_analysis,
    analyze_request,
    requires_render_application,
    safe_audit_text,
)


def graph(
    *,
    execution_attempt: object = None,
    with_kb: bool = True,
    specialist_code: str = "data-specialist",
    specialist_tools: list[str] | None = None,
) -> SimpleNamespace:
    specialist = SimpleNamespace(
        key="specialist",
        code=specialist_code,
        name="数据专家",
        description="分析授权业务数据",
        model="test-model",
        tool_names=specialist_tools or ["data_preview_query_tool"],
        agent_tools=[],
        handoffs=[],
    )
    unreachable = SimpleNamespace(
        key="unreachable",
        code="unreachable-agent",
        name="不可达智能体",
        description="不应进入本轮能力目录",
        model="test-model",
        tool_names=["unreachable-tool"],
        agent_tools=[],
        handoffs=[],
    )
    root = SimpleNamespace(
        key="root",
        code="root-agent",
        name="主智能体",
        description="理解请求并组织执行",
        model="test-model",
        tool_names=["knowledge_base_search_tool", "local-validator"],
        agent_tools=[SimpleNamespace(target_key="specialist", tool_name="ask_data_specialist")],
        handoffs=[],
    )
    context = {
        "knowledgeBases": (
            [{"kbCode": "policy-kb", "name": "制度知识库", "description": "已授权制度资料"}]
            if with_kb
            else []
        ),
        "clientContext": {
            "assistantContext": {"page": "orders", "selection": ["status", "amount"]}
        },
    }
    if execution_attempt is not None:
        context["executionAttempt"] = execution_attempt
    return SimpleNamespace(
        payload={
            "run": {
                "runId": "run-1",
                "requestId": "request-1",
                "input": "分析订单异常并给出可验证结论",
                "context": context,
                "timeoutMs": 120_000,
            },
            "messages": [
                {"role": "assistant", "content": "请说明要分析的范围。"},
                {"role": "user", "content": "分析订单异常并给出可验证结论"},
            ],
        },
        root_key="root",
        root=root,
        agents={"root": root, "specialist": specialist, "unreachable": unreachable},
        gateway_tools={},
        max_turns=4,
        max_depth=4,
    )


def draft(**overrides: object) -> RequestAnalysisDraft:
    value = {
        "goal": "识别订单异常的原因与影响范围",
        "deliverable": "异常结论、证据依据和后续处理建议",
        "constraints": ["只使用已授权数据", "不执行写操作"],
        "gaps": ["尚未确认统计时间范围"],
        "route": {
            "mode": "TOOL",
            "agent_code": "root-agent",
            "tool_codes": ["local-validator"],
            "knowledge_base_codes": ["policy-kb"],
            "rationale": "先校验已有信息，再形成结论。",
        },
        "confidence": {
            "overall": 0.86,
            "intent_clarity": 0.92,
            "context_sufficiency": 0.68,
            "route_fit": 0.9,
            "basis": ["目标和交付物明确", "统计时间范围仍待确认"],
        },
        "execution_readiness": {
            "score": 0.72,
            "level": "READY",
            "reason": "目标清晰，但仍缺少时间范围。",
        },
        "success_criteria": ["结论回答异常原因", "每个事实主张可验证"],
        "validation_plan": ["核对工具结果", "最终执行可信度评估"],
        "low_readiness_remediation": [
            {
                "action": "QUERY_KNOWLEDGE_BASE",
                "target_code": "policy-kb",
                "description": "查询制度知识库补充异常口径。",
            }
        ],
    }
    value.update(overrides)
    return RequestAnalysisDraft.model_validate(value)


def render_workflow_snapshot() -> dict[str, object]:
    return {
        "workflowRef": "workflow://render-document-delivery/v1",
        "apiVersion": "ai.platform/v1alpha1",
        "kind": "ArtifactWorkflow",
        "metadata": {"code": "render-document-delivery", "version": 1},
        "spec": {
            "artifacts": [
                {
                    "code": "final-answer",
                    "artifactType": "TEXT",
                    "contentFormat": "MARKDOWN",
                    "required": True,
                    "inlineSchema": {"type": "string"},
                },
                {
                    "code": "data-preview",
                    "artifactType": "JSON",
                    "contentFormat": "JSON",
                    "required": True,
                    "inlineSchema": {
                        "type": "object",
                        "required": [
                            "tool",
                            "success",
                            "model",
                            "catalogVersion",
                            "sourceRevision",
                            "columns",
                            "records",
                        ],
                        "properties": {
                            "tool": {
                                "type": "string",
                                "enum": ["data_preview_query_tool"],
                            },
                            "success": {"type": "boolean", "enum": [True]},
                            "model": {"type": "string"},
                            "catalogVersion": {"type": "integer"},
                            "sourceRevision": {"type": "string"},
                            "columns": {"type": "array"},
                            "records": {"type": "array"},
                        },
                    },
                },
                {
                    "code": "render-document",
                    "artifactType": "RENDER_JSON",
                    "contentFormat": "JSON",
                    "required": True,
                    "inlineSchema": {
                        "type": "object",
                        "required": ["protocol", "protocolVersion", "pageId", "root"],
                        "properties": {
                            "protocol": {"type": "string", "enum": ["render-json"]},
                            "protocolVersion": {
                                "type": "string",
                                "enum": ["1.0", "1.0.0"],
                            },
                            "pageId": {"type": "string"},
                            "root": {"type": "object"},
                        },
                    },
                },
                {
                    "code": "validation-report",
                    "artifactType": "JSON",
                    "contentFormat": "JSON",
                    "required": True,
                    "inlineSchema": {
                        "type": "object",
                        "required": ["tool", "valid"],
                        "properties": {
                            "tool": {
                                "type": "string",
                                "enum": ["render_json_validate_tool"],
                            },
                            "valid": {"type": "boolean", "enum": [True]},
                        },
                    },
                },
            ],
            "completionPolicy": {
                "requireAllRequiredArtifacts": True,
                "requireAllBlockingChecksPassed": True,
            },
            "repairPolicy": {"maxRepairAttempts": 1, "onExhausted": "FAILED"},
        },
    }


class RequestAnalysisContractTest(unittest.TestCase):
    def test_render_workflow_requires_all_four_trusted_artifact_contracts(self) -> None:
        complete = {"workflowSnapshot": render_workflow_snapshot()}
        incomplete_workflow = render_workflow_snapshot()
        incomplete_workflow["spec"]["artifacts"] = [
            artifact
            for artifact in incomplete_workflow["spec"]["artifacts"]
            if artifact["code"] != "validation-report"
        ]

        self.assertIs(True, runner._requires_render_document_workflow(complete))
        self.assertIs(
            False,
            runner._requires_render_document_workflow(
                {"workflowSnapshot": incomplete_workflow}
            ),
        )

    def test_pydantic_list_default_is_isolated_per_analysis(self) -> None:
        first = RequestConfidenceDraft(
            overall=0.8,
            intent_clarity=0.8,
            context_sufficiency=0.8,
            route_fit=0.8,
        )
        second = RequestConfidenceDraft(
            overall=0.8,
            intent_clarity=0.8,
            context_sufficiency=0.8,
            route_fit=0.8,
        )

        first.basis.append("first-only")

        self.assertEqual(["first-only"], first.basis)
        self.assertEqual([], second.basis)

    def test_exposes_auditable_value_and_confidence_basis(self) -> None:
        runtime_graph = graph()
        analysis = _validate_analysis(draft(), runtime_graph, "分析订单异常并给出可验证结论")

        self.assertEqual("SUCCESS", analysis.status)
        self.assertEqual("PARTIAL", analysis.execution_readiness.level)
        self.assertEqual(("local-validator",), analysis.route.tool_codes)
        self.assertEqual(("policy-kb",), analysis.route.knowledge_base_codes)
        summary = analysis.output_summary()
        self.assertIn("目标：识别订单异常的原因与影响范围", summary)
        self.assertIn("建议路线", summary)
        self.assertIn("理解置信度：86%", summary)
        self.assertIn("置信度依据：目标和交付物明确", summary)
        self.assertIn("执行就绪度：72%（部分就绪）", summary)
        self.assertIn("成功标准", summary)
        self.assertIn("验证计划", summary)
        self.assertIn("就绪度不足时", summary)

        event_ext = analysis.event_ext(runtime_graph)
        self.assertEqual(2, event_ext["analysisSchemaVersion"])
        self.assertEqual("RECOMMENDATION", event_ext["routeNature"])
        self.assertEqual("REQUEST_ROUTING", event_ext["confidenceKind"])
        self.assertEqual(0.86, event_ext["confidence"])
        self.assertEqual("识别订单异常的原因与影响范围", event_ext["analysis"]["goal"])
        self.assertEqual(
            {"key": "root-agent", "name": "主智能体"},
            event_ext["analysis"]["route"]["agent"],
        )
        self.assertEqual(
            [{"key": "local-validator", "name": "local-validator"}],
            event_ext["analysis"]["route"]["tools"],
        )

    def test_filters_unreachable_or_uninstalled_capabilities(self) -> None:
        unsafe = draft(
            route={
                "mode": "DELEGATE",
                "agent_code": "unreachable-agent",
                "tool_codes": ["unreachable-tool"],
                "knowledge_base_codes": ["secret-kb"],
                "rationale": "Use capabilities outside the reachable graph.",
            },
            low_readiness_remediation=[
                {
                    "action": "USE_TOOL",
                    "target_code": "unreachable-tool",
                    "description": "Call an unavailable tool.",
                }
            ],
        )

        analysis = _validate_analysis(unsafe, graph(), "分析订单异常")

        self.assertEqual("DEGRADED", analysis.status)
        self.assertEqual("DIRECT", analysis.route.mode)
        self.assertEqual("root-agent", analysis.route.agent_code)
        self.assertEqual((), analysis.route.tool_codes)
        self.assertLessEqual(analysis.confidence.overall, 0.49)
        self.assertEqual("LOW", analysis.execution_readiness.level)
        self.assertTrue(any("未授权协作智能体" in item for item in analysis.validation_warnings))
        self.assertEqual("DEGRADED", analysis.event_ext()["analysisStatus"])
        self.assertEqual("RECOMMENDATION", analysis.event_ext()["routeNature"])

    def test_catalog_contains_only_reachable_and_installed_capabilities(self) -> None:
        payload = _analysis_input(graph(with_kb=False), "分析订单异常")

        self.assertEqual(
            {"data-specialist", "root-agent"},
            {item["code"] for item in payload["allowedAgents"]},
        )
        self.assertNotIn("unreachable-tool", payload["allowedToolCodes"])
        self.assertNotIn("knowledge_base_search_tool", payload["allowedToolCodes"])
        tools = {item["key"]: item["name"] for item in payload["allowedTools"]}
        self.assertEqual("查询数据预览", tools["data_preview_query_tool"])
        self.assertEqual("local-validator", tools["local-validator"])
        self.assertEqual([], payload["allowedKnowledgeBases"])

    def test_render_application_classifier_excludes_schema_only_requests(self) -> None:
        requests = (
            "不要展示数据库实际数据，只用表格说明字段含义。",
            "无需查询具体记录，请用表格说明数据字段。",
            "请用表格展示 ods_trade_account_user_address 表的数据字段和含义。",
            "不要展示 ods_trade_account_user_address 的全部记录，用表格说明字段含义。",
        )

        for request in requests:
            with self.subTest(request=request):
                self.assertIs(False, requires_render_application(request))

    def test_render_application_classifier_accepts_concrete_list_requests(self) -> None:
        requests = (
            "请展示 ods_trade_account_user_address 列表数据。",
            "我想看看用户地址都有具体哪些数据，用数据列表展示吧。",
            "不要只说明字段含义，请展示 ods_trade_account_user_address 的实际列表数据。",
            "请展示 ods_trade_account_user_address 列表数据，并说明字段含义。",
        )

        for request in requests:
            with self.subTest(request=request):
                self.assertIs(True, requires_render_application(request))

    def test_render_application_classifier_accepts_limited_subset_after_full_set_negation(
        self,
    ) -> None:
        requests = (
            "不要展示数据库全部数据，只展示 ods_trade_account_user_address 最近 10 条记录，用表格展示。",
            "所有数据不要展示，只展示 ods_trade_account_user_address 前 5 条记录，用表格展示。",
            "请勿显示全量记录；请列出 ods_trade_account_user_address 最新 20 条记录，以表格形式展示。",
        )

        for request in requests:
            with self.subTest(request=request):
                self.assertIs(True, requires_render_application(request))

    def test_explicit_database_records_list_is_routed_to_render_builder(self) -> None:
        runtime_graph = graph(
            specialist_code="dashboard-application-builder",
            specialist_tools=[
                "knowledge_base_search_tool",
                "data_preview_query_tool",
                "render_json_validate_tool",
            ],
        )
        clarify = draft(
            route={
                "mode": "CLARIFY",
                "agent_code": "root-agent",
                "tool_codes": [],
                "knowledge_base_codes": ["policy-kb"],
                "rationale": "先询问数据库类型。",
            }
        )

        analysis = _validate_analysis(
            clarify,
            runtime_graph,
            "我想看看用户地址都有具体哪些数据，用数据列表展示吧。",
        )

        self.assertEqual("DELEGATE", analysis.route.mode)
        self.assertEqual("dashboard-application-builder", analysis.route.agent_code)
        self.assertIn("data_preview_query_tool", analysis.route.tool_codes)
        self.assertIn("render_json_validate_tool", analysis.route.tool_codes)
        self.assertIn("不能退化为字段说明", analysis.route.rationale)
        tools = {
            item["key"]: item["name"]
            for item in analysis.event_ext(runtime_graph)["analysis"]["route"]["tools"]
        }
        self.assertEqual("查询数据预览", tools["data_preview_query_tool"])
        self.assertEqual("校验 Render JSON", tools["render_json_validate_tool"])

    def test_named_warehouse_table_list_data_is_routed_to_render_builder(self) -> None:
        runtime_graph = graph(
            specialist_code="dashboard-application-builder",
            specialist_tools=["data_preview_query_tool", "render_json_validate_tool"],
        )

        analysis = _validate_analysis(
            draft(),
            runtime_graph,
            "请展示 ods_trade_account_user_address 列表数据。",
        )

        self.assertEqual("DELEGATE", analysis.route.mode)
        self.assertEqual("dashboard-application-builder", analysis.route.agent_code)

    def test_field_description_table_does_not_force_render_builder(self) -> None:
        runtime_graph = graph(
            specialist_code="dashboard-application-builder",
            specialist_tools=["data_preview_query_tool", "render_json_validate_tool"],
        )
        clarify = draft(
            route={
                "mode": "CLARIFY",
                "agent_code": "root-agent",
                "tool_codes": [],
                "knowledge_base_codes": [],
                "rationale": "先确认字段范围。",
            }
        )

        analysis = _validate_analysis(
            clarify,
            runtime_graph,
            "用户地址有哪些字段？用表格说明字段含义。",
        )

        self.assertEqual("CLARIFY", analysis.route.mode)
        self.assertEqual("root-agent", analysis.route.agent_code)

    def test_activity_identity_is_scoped_by_execution_attempt(self) -> None:
        default = runner._attempt_activity_code(graph(), "main-agent-request-analysis")
        first = runner._attempt_activity_code(graph(execution_attempt=1), "main-agent-request-analysis")
        same = runner._attempt_activity_code(graph(execution_attempt=1), "main-agent-request-analysis")
        second = runner._attempt_activity_code(graph(execution_attempt="repair/2"), "main-agent-request-analysis")

        self.assertEqual("main-agent-request-analysis:1", default)
        self.assertEqual(default, first)
        self.assertEqual(first, same)
        self.assertEqual("main-agent-request-analysis:repair-2", second)

    def test_persisted_summary_redacts_credentials(self) -> None:
        runtime_graph = graph()
        runtime_graph.payload["run"]["input"] = (
            "Authorization: Bearer token-123 password=hunter2"
        )
        summary = runner._request_analysis_summary(runtime_graph)
        safe_value = safe_audit_text("api_key=sk-example123456", 200)

        self.assertNotIn("token-123", summary)
        self.assertNotIn("hunter2", summary)
        self.assertNotIn("sk-example123456", safe_value)

    def test_extracts_analysis_usage_from_sdk_context(self) -> None:
        result = SimpleNamespace(
            context_wrapper=SimpleNamespace(
                usage=SimpleNamespace(input_tokens=13, output_tokens=5, total_tokens=18)
            )
        )

        self.assertEqual(
            {"inputTokens": 13, "outputTokens": 5, "totalTokens": 18},
            _extract_usage(result),
        )

    def test_conclusion_summary_preserves_markdown_line_breaks(self) -> None:
        markdown = (
            "`ods_trade_account_user_address` 表字段如下：\r\n"
            "| 字段编码 | 字段名称 | 类型 |\r\n"
            "|---|---|---|\r\n"
            "| user_id | 用户 ID | bigint |"
        )

        summary = runner._conclusion_summary(markdown)
        normalized = markdown.replace("\r\n", "\n").strip()

        self.assertNotIn("\r", summary)
        self.assertIn("\n| 字段编码 | 字段名称 | 类型 |\n|---|---|---|", summary)
        self.assertEqual(f"结论摘要：{normalized}", summary)

    def test_compact_summary_respects_limit_when_truncating_markdown(self) -> None:
        markdown = "第一行\n第二行\n第三行"
        limit = 8

        summary = runner._compact_summary(markdown, limit)

        normalized = markdown.strip()
        self.assertEqual(limit, len(summary))
        self.assertTrue(summary.endswith("…"))
        self.assertEqual(normalized[: limit - 1], summary[:-1])

    def test_compact_summary_handles_non_positive_limit(self) -> None:
        self.assertEqual("", runner._compact_summary("some content", 0))
        self.assertEqual("", runner._compact_summary("some content", -1))


class RequestAnalysisFallbackTest(unittest.IsolatedAsyncioTestCase):
    async def test_analysis_error_returns_degraded_fallback_and_safe_remediation(self) -> None:
        with patch(
            "agent_provider.runtime.request_analysis._run_analysis_agent",
            new=AsyncMock(side_effect=ValueError("invalid structured output")),
        ):
            analysis = await analyze_request(graph(), "分析订单异常", model="test-model")

        self.assertEqual("DEGRADED", analysis.status)
        self.assertEqual("ValueError", analysis.degraded_reason)
        self.assertEqual("LOW", analysis.execution_readiness.level)
        self.assertEqual("QUERY_KNOWLEDGE_BASE", analysis.low_readiness_remediation[0].action)
        self.assertGreaterEqual(analysis.duration_ms, 0)

    async def test_analysis_error_still_applies_explicit_render_route(self) -> None:
        runtime_graph = graph(
            specialist_code="dashboard-application-builder",
            specialist_tools=["data_preview_query_tool", "render_json_validate_tool"],
        )
        request = "请展示 ods_trade_account_user_address 列表数据。"
        with patch(
            "agent_provider.runtime.request_analysis._run_analysis_agent",
            new=AsyncMock(side_effect=ValueError("invalid structured output")),
        ):
            analysis = await analyze_request(runtime_graph, request, model="test-model")

        self.assertEqual("DEGRADED", analysis.status)
        self.assertEqual("DELEGATE", analysis.route.mode)
        self.assertEqual("dashboard-application-builder", analysis.route.agent_code)

    async def test_analysis_timeout_is_enforced_and_degrades_without_blocking(self) -> None:
        async def blocked_analysis(*args: object, **kwargs: object):
            await asyncio.Event().wait()

        with (
            patch(
                "agent_provider.runtime.request_analysis._run_analysis_agent",
                new=blocked_analysis,
            ),
            patch(
                "agent_provider.runtime.request_analysis._analysis_timeout_seconds",
                return_value=0.001,
            ),
        ):
            analysis = await analyze_request(graph(), "分析订单异常", model="test-model")

        self.assertEqual("DEGRADED", analysis.status)
        self.assertEqual("TimeoutError", analysis.degraded_reason)
        self.assertEqual("LOW", analysis.execution_readiness.level)
        self.assertEqual(
            {"inputTokens": 0, "outputTokens": 0, "totalTokens": 0},
            analysis.usage,
        )


class RunnerRequestAnalysisIntegrationTest(unittest.IsolatedAsyncioTestCase):
    async def test_analysis_precedes_main_run_and_guarded_output_is_authoritative(self) -> None:
        runtime_graph = graph(execution_attempt=3)
        analysis = replace(
            _validate_analysis(draft(), runtime_graph, "分析订单异常并给出可验证结论"),
            status="DEGRADED",
            degraded_reason="TimeoutError",
            duration_ms=7,
            usage={"inputTokens": 3, "outputTokens": 2, "totalTokens": 5},
        )
        original = '{"artifacts":[{"code":"original","content":{"ok":false}}]}'
        revised = '{"artifacts":[{"code":"guarded","content":{"ok":true}}]}'
        order: list[str] = []
        run_agents: list[object] = []
        frames: list[dict[str, object]] = []

        class MainResult:
            final_output = original
            last_agent = None
            context_wrapper = SimpleNamespace(
                usage=SimpleNamespace(input_tokens=11, output_tokens=7, total_tokens=18)
            )

            async def stream_events(self):
                if False:
                    yield None

        class FakeRunner:
            @staticmethod
            def run_streamed(*args: object, **kwargs: object) -> MainResult:
                order.append("main-run")
                run_agents.append(args[0])
                return MainResult()

        def write(frame: dict[str, object]) -> None:
            frames.append(frame)
            if frame.get("eventType"):
                order.append(str(frame["eventType"]))

        async def analyze(*args: object, **kwargs: object):
            order.append("structured-analysis")
            return analysis

        sdk_root = object()
        artifact_collector = RunArtifactCollector()
        artifact_collector.collect_output(
            '{"artifacts":['
            '{"code":"delegated","content":{"source":"specialist"}},'
            '{"code":"original","content":{"source":"specialist"}},'
            '{"code":"guarded","content":{"source":"specialist"}}'
            ']}'
        )
        sdk_graph = SimpleNamespace(
            root=sdk_root,
            compiled_for=lambda value: None,
            artifact_collector=artifact_collector,
        )
        guarded = SimpleNamespace(text=revised, audit_dict=lambda: {"enabled": False})
        guard = AsyncMock(return_value=guarded)

        with (
            patch.dict(sys.modules, {"agents": SimpleNamespace(Runner=FakeRunner)}),
            patch.object(runner, "build_sdk_graph", return_value=sdk_graph),
            patch.object(runner, "analyze_request", side_effect=analyze),
            patch.object(runner, "guard_output", new=guard),
        ):
            result = await runner.run_graph(runtime_graph, EventEmitter(runtime_graph.payload, write))

        self.assertLess(order.index("thinking.analysis.completed"), order.index("main-run"))
        completed = next(
            frame for frame in frames if frame.get("eventType") == "thinking.analysis.completed"
        )
        self.assertEqual("SUCCESS", completed["status"])
        self.assertEqual("main-agent-request-analysis:3", completed["ext"]["activityCode"])
        self.assertEqual("DEGRADED", completed["ext"]["analysisStatus"])
        self.assertEqual("RECOMMENDATION", completed["ext"]["routeNature"])
        self.assertIs(sdk_root, run_agents[0])
        self.assertIs(sdk_root, guard.await_args.kwargs["sdk_agent"])
        self.assertIs(runtime_graph.root, guard.await_args.kwargs["compiled_agent"])
        self.assertEqual(
            {"inputTokens": 3, "outputTokens": 2, "totalTokens": 5},
            completed["ext"]["analysisUsage"],
        )
        self.assertEqual(revised, result["finalOutput"])
        self.assertEqual(revised, result["outputs"][0]["text"])
        self.assertEqual(
            ["delegated", "original", "guarded"],
            [artifact["artifactCode"] for artifact in result["artifacts"]],
        )
        artifacts_by_code = {
            artifact["artifactCode"]: artifact for artifact in result["artifacts"]
        }
        self.assertEqual(False, artifacts_by_code["original"]["content"]["ok"])
        self.assertEqual(True, artifacts_by_code["guarded"]["content"]["ok"])
        self.assertEqual(
            ["delegated", "original", "guarded"],
            [
                frame["ext"]["artifactCode"]
                for frame in frames
                if frame.get("eventType") == "artifact.created"
            ],
        )
        self.assertEqual(
            {"inputTokens": 14, "outputTokens": 9, "totalTokens": 23},
            result["usage"],
        )
        self.assertEqual(
            {
                "status": "DEGRADED",
                "durationMs": 7,
                "usage": {"inputTokens": 3, "outputTokens": 2, "totalTokens": 5},
                "routeApplied": False,
                "selectedAgentCode": "root-agent",
                "routeSource": "ROOT",
            },
            result["providerMeta"]["requestAnalysis"],
        )

    async def test_render_route_uses_real_guard_passthrough_without_exposing_artifact_json(self) -> None:
        runtime_graph = graph(
            specialist_code="dashboard-application-builder",
            specialist_tools=["data_preview_query_tool", "render_json_validate_tool"],
        )
        request = "请展示 ods_trade_account_user_address 列表数据。"
        runtime_graph.payload["run"]["input"] = request
        runtime_graph.payload["messages"] = [{"role": "user", "content": request}]
        analysis = _validate_analysis(draft(), runtime_graph, request)
        specialist = runtime_graph.agents["specialist"]
        sdk_root = object()
        sdk_specialist = object()
        built_keys: list[str] = []
        run_agents: list[object] = []
        frames: list[dict[str, object]] = []
        render_document = {
            "protocol": "render-json",
            "protocolVersion": "1.0.0",
            "pageId": "addresses",
            "root": {
                "component": "zg-common-list",
                "datasource": {
                    "type": "db-query-list",
                    "model": "ods_trade_account_user_address",
                    "fields": ["address"],
                },
            },
        }
        preview_proof = {
            "tool": "data_preview_query_tool",
            "success": True,
            "model": "ods_trade_account_user_address",
            "queryType": "LIST",
            "catalogVersion": 1,
            "sourceRevision": "virtual-model/v1",
            "columns": [{"name": "address"}],
            "records": [{"address": "trusted-address"}],
        }
        validation_proof = {
            "tool": "render_json_validate_tool",
            "valid": True,
            "documentHash": render_document_hash(render_document),
            "renderDocument": render_document,
        }
        raw_output = json.dumps(
            {
                "artifacts": [
                    {
                        "artifactCode": "render-document",
                        "artifactType": "RENDER_JSON",
                        "contentFormat": "application/json; charset=utf-8",
                        "content": render_document,
                    },
                    {
                        "artifactCode": "data-preview",
                        "artifactType": "JSON",
                        "content": {"forged": True},
                    },
                    {
                        "artifactCode": "validation-report",
                        "artifactType": "JSON",
                        "content": {"forged": True},
                    },
                ]
            }
        )

        class SpecialistResult:
            final_output = raw_output
            last_agent = sdk_specialist

            async def stream_events(self):
                yield SimpleNamespace(
                    type="raw_response_event",
                    data=SimpleNamespace(
                        type="response.output_text.delta",
                        delta=raw_output,
                    ),
                )
                for call_id, tool_code, output in (
                    ("preview-1", "data_preview_query_tool", preview_proof),
                    ("validate-1", "render_json_validate_tool", validation_proof),
                ):
                    yield SimpleNamespace(
                        type="run_item_stream_event",
                        name="tool_called",
                        item=SimpleNamespace(
                            raw_item=SimpleNamespace(name=tool_code, call_id=call_id)
                        ),
                    )
                    yield SimpleNamespace(
                        type="run_item_stream_event",
                        name="tool_output",
                        item=SimpleNamespace(
                            raw_item=SimpleNamespace(
                                type="function_call_output",
                                call_id=call_id,
                            ),
                            output=output,
                        ),
                    )

        class FakeRunner:
            @staticmethod
            def run_streamed(*args: object, **kwargs: object) -> SpecialistResult:
                run_agents.append(args[0])
                return SpecialistResult()

        def agent_for_key(key: str) -> object:
            built_keys.append(key)
            return sdk_specialist

        collector = RunArtifactCollector()
        sdk_graph = SimpleNamespace(
            root=sdk_root,
            agent_for_key=agent_for_key,
            compiled_for=lambda value: specialist if value is sdk_specialist else runtime_graph.root,
            artifact_collector=collector,
        )

        with (
            patch.dict(sys.modules, {"agents": SimpleNamespace(Runner=FakeRunner)}),
            patch.object(runner, "build_sdk_graph", return_value=sdk_graph),
            patch.object(runner, "analyze_request", new=AsyncMock(return_value=analysis)),
        ):
            result = await runner.run_graph(
                runtime_graph,
                EventEmitter(runtime_graph.payload, frames.append),
            )

        self.assertEqual(["specialist"], built_keys)
        self.assertEqual([sdk_specialist], run_agents)
        self.assertEqual("dashboard-application-builder", result["finalAgentCode"])
        self.assertEqual("dashboard-application-builder", result["providerMeta"]["lastAgent"])
        self.assertEqual(
            "已生成并校验 `ods_trade_account_user_address` 的数据列表"
            "（预览 1 条记录），可在下方查看。",
            result["finalOutput"],
        )
        self.assertEqual(result["finalOutput"], result["outputs"][0]["text"])
        self.assertNotIn('"artifacts"', result["finalOutput"])
        self.assertFalse(
            any(frame.get("eventType") == "assistant.message.delta" for frame in frames)
        )
        self.assertEqual(
            {
                "status": analysis.status,
                "durationMs": analysis.duration_ms,
                "usage": analysis.usage,
                "routeApplied": True,
                "selectedAgentCode": "dashboard-application-builder",
                "routeSource": "REQUEST_ANALYSIS",
            },
            result["providerMeta"]["requestAnalysis"],
        )
        artifacts = {
            artifact["artifactCode"]: artifact for artifact in result["artifacts"]
        }
        self.assertEqual(
            {"render-document", "data-preview", "validation-report"},
            set(artifacts),
        )
        self.assertEqual("JSON", artifacts["render-document"]["contentFormat"])
        self.assertEqual(
            "trusted-address",
            artifacts["data-preview"]["content"]["records"][0]["address"],
        )
        self.assertEqual(preview_proof, artifacts["data-preview"]["content"])
        self.assertNotIn("forged", artifacts["data-preview"]["content"])
        self.assertEqual(
            render_document_hash(render_document),
            artifacts["validation-report"]["content"]["documentHash"],
        )
        self.assertEqual(
            {key: value for key, value in validation_proof.items() if key != "renderDocument"},
            artifacts["validation-report"]["content"],
        )
        self.assertNotIn("forged", artifacts["validation-report"]["content"])
        self.assertEqual("render-document", collector.snapshot()[0]["artifactCode"])
        analysis_completed = next(
            frame for frame in frames if frame.get("eventType") == "thinking.analysis.completed"
        )
        self.assertEqual("APPLIED", analysis_completed["ext"]["routeNature"])
        self.assertIs(True, analysis_completed["ext"]["routeApplied"])

    async def test_explicit_builder_clarification_never_streams_artifact_envelope(self) -> None:
        runtime_graph = graph(specialist_code="data-analysis")
        runtime_graph.root.code = "dashboard-application-builder"
        request = "帮我做个看板。"
        runtime_graph.payload["run"]["input"] = request
        runtime_graph.payload["messages"] = [{"role": "user", "content": request}]
        analysis = _validate_analysis(
            draft(
                route={
                    "mode": "CLARIFY",
                    "agent_code": "dashboard-application-builder",
                    "tool_codes": [],
                    "knowledge_base_codes": [],
                    "rationale": "需要先确认数据范围和展示指标。",
                },
                execution_readiness={
                    "score": 0.2,
                    "level": "LOW",
                    "reason": "缺少数据范围和展示指标。",
                },
            ),
            runtime_graph,
            request,
        )
        sdk_root = object()
        frames: list[dict[str, object]] = []
        raw_output = json.dumps(
            {
                "artifacts": [
                    {
                        "artifactCode": "render-document",
                        "artifactType": "RENDER_JSON",
                        "contentFormat": "JSON",
                        "content": {
                            "protocol": "render-json",
                            "protocolVersion": "1.0.0",
                            "pageId": "premature-dashboard",
                            "root": {"component": "zg-common-list"},
                        },
                    }
                ]
            }
        )

        class ClarificationResult:
            final_output = raw_output
            last_agent = sdk_root

            async def stream_events(self):
                yield SimpleNamespace(
                    type="raw_response_event",
                    data=SimpleNamespace(
                        type="response.output_text.delta",
                        delta=raw_output,
                    ),
                )

        class FakeRunner:
            @staticmethod
            def run_streamed(*args: object, **kwargs: object) -> ClarificationResult:
                return ClarificationResult()

        sdk_graph = SimpleNamespace(
            root=sdk_root,
            compiled_for=lambda value: runtime_graph.root,
            artifact_collector=RunArtifactCollector(),
        )

        with (
            patch.dict(sys.modules, {"agents": SimpleNamespace(Runner=FakeRunner)}),
            patch.object(runner, "build_sdk_graph", return_value=sdk_graph),
            patch.object(runner, "analyze_request", new=AsyncMock(return_value=analysis)),
        ):
            result = await runner.run_graph(
                runtime_graph,
                EventEmitter(runtime_graph.payload, frames.append),
            )

        self.assertEqual("INPUT_REQUIRED", result["status"])
        self.assertEqual([], result["artifacts"])
        self.assertNotIn('"artifacts"', result["finalOutput"])
        self.assertIn("未形成", result["finalOutput"])
        self.assertFalse(
            any(frame.get("eventType") == "assistant.message.delta" for frame in frames)
        )
        self.assertEqual(
            {
                "status": analysis.status,
                "durationMs": analysis.duration_ms,
                "usage": analysis.usage,
                "routeApplied": False,
                "selectedAgentCode": "dashboard-application-builder",
                "routeSource": "ROOT",
            },
            result["providerMeta"]["requestAnalysis"],
        )

    def test_render_workflow_keeps_specialist_route_for_non_matching_repair_text(self) -> None:
        runtime_graph = graph(
            specialist_code="dashboard-application-builder",
            specialist_tools=["data_preview_query_tool", "render_json_validate_tool"],
        )
        runtime_graph.payload["workflowSnapshot"] = render_workflow_snapshot()
        runtime_graph.payload["run"]["input"] = "请根据验收失败信息修复缺少的产物。"
        analysis = _validate_analysis(
            draft(),
            runtime_graph,
            runtime_graph.payload["run"]["input"],
        )
        sdk_root = object()
        sdk_specialist = object()
        built_keys: list[str] = []
        sdk_graph = SimpleNamespace(
            root=sdk_root,
            agent_for_key=lambda key: built_keys.append(key) or sdk_specialist,
        )

        selected, compiled, applied, source = runner._execution_route(
            runtime_graph,
            sdk_graph,
            analysis,
        )

        self.assertIs(sdk_specialist, selected)
        self.assertIs(runtime_graph.agents["specialist"], compiled)
        self.assertIs(applied, True)
        self.assertEqual("WORKFLOW_SNAPSHOT", source)
        self.assertEqual(["specialist"], built_keys)

    def test_only_validated_input_request_returns_input_required(self) -> None:
        runtime_graph = graph(
            specialist_code="dashboard-application-builder",
            specialist_tools=["data_preview_query_tool", "render_json_validate_tool"],
        )
        clarify = _validate_analysis(
            draft(
                route={
                    "mode": "CLARIFY",
                    "agent_code": "root-agent",
                    "tool_codes": [],
                    "knowledge_base_codes": [],
                    "rationale": "先确认看板的数据范围和指标。",
                },
                execution_readiness={
                    "score": 0.2,
                    "level": "LOW",
                    "reason": "缺少数据范围和展示指标。",
                },
            ),
            runtime_graph,
            "帮我做个看板。",
        )
        build = _validate_analysis(
            draft(
                route={
                    "mode": "CLARIFY",
                    "agent_code": "root-agent",
                    "tool_codes": [],
                    "knowledge_base_codes": [],
                    "rationale": "先确认数据范围。",
                }
            ),
            runtime_graph,
            "请展示 ods_trade_account_user_address 列表数据。",
        )
        low_direct = _validate_analysis(
            draft(
                route={
                    "mode": "DIRECT",
                    "agent_code": "root-agent",
                    "tool_codes": [],
                    "knowledge_base_codes": [],
                    "rationale": "先请用户补齐看板范围。",
                },
                execution_readiness={
                    "score": 0.2,
                    "level": "LOW",
                    "reason": "缺少数据范围和展示指标。",
                },
                low_readiness_remediation=[
                    {
                        "action": "ASK_USER",
                        "target_code": None,
                        "description": "请用户补充数据范围和指标。",
                    }
                ],
            ),
            runtime_graph,
            "帮我做个看板。",
        )

        self.assertEqual("CLARIFY", clarify.route.mode)
        self.assertEqual("INPUT_REQUIRED", runner._result_status(clarify))
        self.assertEqual("INPUT_REQUIRED", runner._result_status(low_direct))
        self.assertEqual(
            "SUCCESS",
            runner._result_status(clarify, route_applied=True),
        )
        self.assertEqual("DELEGATE", build.route.mode)
        self.assertEqual("SUCCESS", runner._result_status(build))
        self.assertEqual(
            "SUCCESS",
            runner._result_status(
                SimpleNamespace(
                    route_validated=True,
                    route=SimpleNamespace(mode="CLARIFY"),
                )
            ),
        )

    def test_workflow_reference_without_required_contract_keeps_root_flow(self) -> None:
        runtime_graph = graph(
            specialist_code="dashboard-application-builder",
            specialist_tools=["data_preview_query_tool", "render_json_validate_tool"],
        )
        runtime_graph.payload["workflowSnapshot"] = {
            "workflowRef": "workflow://render-document-delivery/v1",
            "apiVersion": "ai.platform/v1alpha1",
            "kind": "ArtifactWorkflow",
            "metadata": {"code": "render-document-delivery", "version": 1},
            "spec": {},
        }
        analysis = _validate_analysis(draft(), runtime_graph, "继续处理当前请求。")
        sdk_root = object()
        sdk_graph = SimpleNamespace(root=sdk_root)

        selected, compiled, applied, source = runner._execution_route(
            runtime_graph,
            sdk_graph,
            analysis,
        )

        self.assertIs(sdk_root, selected)
        self.assertIs(runtime_graph.root, compiled)
        self.assertIs(applied, False)
        self.assertEqual("ROOT", source)

    def test_model_dashboard_recommendation_without_record_list_signal_keeps_root_flow(self) -> None:
        runtime_graph = graph(
            specialist_code="dashboard-application-builder",
            specialist_tools=["data_preview_query_tool", "render_json_validate_tool"],
        )
        request = "帮我整理一下当前需求。"
        runtime_graph.payload["run"]["input"] = request
        analysis = _validate_analysis(
            draft(
                route={
                    "mode": "DELEGATE",
                    "agent_code": "dashboard-application-builder",
                    "tool_codes": ["data_preview_query_tool", "render_json_validate_tool"],
                    "knowledge_base_codes": [],
                    "rationale": "模型建议使用看板构建 Agent。",
                }
            ),
            runtime_graph,
            request,
        )
        self.assertEqual("DELEGATE", analysis.route.mode)
        sdk_root = object()
        sdk_graph = SimpleNamespace(root=sdk_root)

        selected, compiled, applied, source = runner._execution_route(
            runtime_graph,
            sdk_graph,
            analysis,
        )

        self.assertIs(sdk_root, selected)
        self.assertIs(runtime_graph.root, compiled)
        self.assertIs(applied, False)
        self.assertEqual("ROOT", source)

    def test_explicit_builder_requires_validated_ready_execution_or_clarification(self) -> None:
        runtime_graph = graph(specialist_code="data-analysis")
        runtime_graph.root.code = "dashboard-application-builder"
        ready = _validate_analysis(
            draft(
                route={
                    "mode": "DIRECT",
                    "agent_code": "dashboard-application-builder",
                    "tool_codes": [],
                    "knowledge_base_codes": [],
                    "rationale": "输入已完整，可直接构建。",
                },
                execution_readiness={
                    "score": 0.92,
                    "level": "READY",
                    "reason": "数据范围和展示指标均已提供。",
                },
            ),
            runtime_graph,
            "根据已提供的数据范围和指标构建销售看板。",
        )
        clarify = _validate_analysis(
            draft(
                route={
                    "mode": "CLARIFY",
                    "agent_code": "dashboard-application-builder",
                    "tool_codes": [],
                    "knowledge_base_codes": [],
                    "rationale": "需要先确认数据范围。",
                },
                execution_readiness={
                    "score": 0.2,
                    "level": "LOW",
                    "reason": "缺少数据范围和展示指标。",
                },
            ),
            runtime_graph,
            "帮我做个看板。",
        )
        sdk_root = object()
        built_keys: list[str] = []
        sdk_graph = SimpleNamespace(
            root=sdk_root,
            agent_for_key=lambda key: built_keys.append(key) or sdk_root,
        )

        selected, compiled, applied, source = runner._execution_route(
            runtime_graph,
            sdk_graph,
            ready,
        )
        clarify_selected, clarify_compiled, clarify_applied, clarify_source = (
            runner._execution_route(runtime_graph, sdk_graph, clarify)
        )

        self.assertIs(sdk_root, selected)
        self.assertIs(runtime_graph.root, compiled)
        self.assertIs(True, applied)
        self.assertEqual("EXPLICIT_TARGET_READINESS", source)
        self.assertEqual(["root"], built_keys)
        self.assertIs(sdk_root, clarify_selected)
        self.assertIs(runtime_graph.root, clarify_compiled)
        self.assertIs(False, clarify_applied)
        self.assertEqual("ROOT", clarify_source)
        self.assertEqual("INPUT_REQUIRED", runner._result_status(clarify))

    def test_validated_builder_route_uses_two_user_turns_for_list_continuation(self) -> None:
        runtime_graph = graph(
            specialist_code="dashboard-application-builder",
            specialist_tools=["data_preview_query_tool", "render_json_validate_tool"],
        )
        current = "用数据列表展示前10条。"
        runtime_graph.payload["run"]["input"] = current
        runtime_graph.payload["messages"] = [
            {
                "role": "user",
                "content": "请查看 ods_trade_account_user_address 的实际记录。",
            },
            {"role": "assistant", "content": "已理解目标表。"},
            {"role": "user", "content": current},
        ]
        analysis = _validate_analysis(
            draft(
                route={
                    "mode": "DELEGATE",
                    "agent_code": "dashboard-application-builder",
                    "tool_codes": ["data_preview_query_tool", "render_json_validate_tool"],
                    "knowledge_base_codes": [],
                    "rationale": "结合最近两轮，请使用列表应用交付真实记录。",
                }
            ),
            runtime_graph,
            current,
        )
        sdk_root = object()
        sdk_specialist = object()
        sdk_graph = SimpleNamespace(
            root=sdk_root,
            agent_for_key=lambda key: sdk_specialist,
        )

        selected, compiled, applied, source = runner._execution_route(
            runtime_graph,
            sdk_graph,
            analysis,
        )

        self.assertIs(False, requires_render_application(current))
        self.assertIs(sdk_specialist, selected)
        self.assertIs(runtime_graph.agents["specialist"], compiled)
        self.assertIs(True, applied)
        self.assertEqual("REQUEST_ANALYSIS", source)

    def test_unvalidated_forged_builder_route_cannot_use_conversation_context(self) -> None:
        runtime_graph = graph(
            specialist_code="dashboard-application-builder",
            specialist_tools=["data_preview_query_tool", "render_json_validate_tool"],
        )
        current = "用数据列表展示前10条。"
        runtime_graph.payload["run"]["input"] = current
        runtime_graph.payload["messages"] = [
            {
                "role": "user",
                "content": "请查看 ods_trade_account_user_address 的实际记录。",
            },
            {"role": "user", "content": current},
        ]
        forged = SimpleNamespace(
            route_validated=True,
            route=SimpleNamespace(
                mode="DELEGATE",
                agent_code="dashboard-application-builder",
            )
        )
        sdk_root = object()
        sdk_graph = SimpleNamespace(root=sdk_root)

        selected, compiled, applied, source = runner._execution_route(
            runtime_graph,
            sdk_graph,
            forged,
        )

        self.assertIs(sdk_root, selected)
        self.assertIs(runtime_graph.root, compiled)
        self.assertIs(False, applied)
        self.assertEqual("ROOT", source)

    async def test_main_knowledge_base_tool_result_is_initial_guard_evidence(self) -> None:
        runtime_graph = graph()
        analysis = _validate_analysis(
            draft(),
            runtime_graph,
            "分析订单异常并给出可验证结论",
        )
        kb_result = {
            "success": True,
            "kbCode": "policy-kb",
            "query": "订单异常口径",
            "items": [
                {
                    "documentId": "policy-1",
                    "content": "订单状态异常需要核对状态流转和统计时间范围。",
                    "score": 0.93,
                }
            ],
        }

        class MainResult:
            final_output = "基于制度知识库形成的原始回答"
            last_agent = None

            async def stream_events(self):
                yield SimpleNamespace(
                    type="run_item_stream_event",
                    name="tool_called",
                    item=SimpleNamespace(
                        raw_item=SimpleNamespace(
                            name="knowledge_base_search_tool",
                            call_id="call-kb-1",
                            arguments='{"kb_code":"policy-kb","query":"订单异常口径"}',
                        )
                    ),
                )
                yield SimpleNamespace(
                    type="run_item_stream_event",
                    name="tool_output",
                    item=SimpleNamespace(
                        raw_item=SimpleNamespace(
                            type="function_call_output",
                            call_id="call-kb-1",
                        ),
                        output=kb_result,
                    ),
                )

        class FakeRunner:
            @staticmethod
            def run_streamed(*args: object, **kwargs: object) -> MainResult:
                return MainResult()

        sdk_graph = SimpleNamespace(root=object(), compiled_for=lambda value: None)
        guarded = SimpleNamespace(
            text="守卫后的回答",
            audit_dict=lambda: {"enabled": True, "scoreStatus": "SCORED"},
        )
        guard = AsyncMock(return_value=guarded)

        with (
            patch.dict(sys.modules, {"agents": SimpleNamespace(Runner=FakeRunner)}),
            patch.object(runner, "build_sdk_graph", return_value=sdk_graph),
            patch.object(runner, "analyze_request", new=AsyncMock(return_value=analysis)),
            patch.object(runner, "guard_output", new=guard),
        ):
            await runner.run_graph(
                runtime_graph,
                EventEmitter(runtime_graph.payload, lambda frame: None),
            )

        guard.assert_awaited_once()
        initial_evidence = guard.await_args.kwargs["initial_evidence"]
        self.assertEqual("policy-kb", initial_evidence["kbCode"])
        self.assertEqual(["policy-kb"], initial_evidence["kbCodes"])
        self.assertEqual("订单异常口径", initial_evidence["query"])
        self.assertEqual("policy-1", initial_evidence["items"][0]["documentId"])
        self.assertIn("状态流转", initial_evidence["items"][0]["content"])


if __name__ == "__main__":
    unittest.main()

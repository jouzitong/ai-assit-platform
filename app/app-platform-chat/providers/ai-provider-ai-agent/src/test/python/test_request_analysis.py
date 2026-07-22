import asyncio
import sys
import unittest
from dataclasses import replace
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from agent_provider.events import EventEmitter
from agent_provider.runtime import runner
from agent_provider.runtime.request_analysis import (
    RequestConfidenceDraft,
    RequestAnalysisDraft,
    _analysis_input,
    _extract_usage,
    _validate_analysis,
    analyze_request,
    safe_audit_text,
)


def graph(*, execution_attempt: object = None, with_kb: bool = True) -> SimpleNamespace:
    specialist = SimpleNamespace(
        key="specialist",
        code="data-specialist",
        name="数据专家",
        description="分析授权业务数据",
        model="test-model",
        tool_names=["data_preview_query_tool"],
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


class RequestAnalysisContractTest(unittest.TestCase):
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
        analysis = _validate_analysis(draft(), graph(), "分析订单异常并给出可验证结论")

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

        event_ext = analysis.event_ext()
        self.assertEqual(1, event_ext["analysisSchemaVersion"])
        self.assertEqual("RECOMMENDATION", event_ext["routeNature"])
        self.assertEqual("REQUEST_ROUTING", event_ext["confidenceKind"])
        self.assertEqual(0.86, event_ext["confidence"])
        self.assertEqual("识别订单异常的原因与影响范围", event_ext["analysis"]["goal"])

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
        self.assertEqual([], payload["allowedKnowledgeBases"])

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
                return MainResult()

        def write(frame: dict[str, object]) -> None:
            frames.append(frame)
            if frame.get("eventType"):
                order.append(str(frame["eventType"]))

        async def analyze(*args: object, **kwargs: object):
            order.append("structured-analysis")
            return analysis

        sdk_root = object()
        sdk_graph = SimpleNamespace(root=sdk_root, compiled_for=lambda value: None)
        guarded = SimpleNamespace(text=revised, audit_dict=lambda: {"enabled": False})

        with (
            patch.dict(sys.modules, {"agents": SimpleNamespace(Runner=FakeRunner)}),
            patch.object(runner, "build_sdk_graph", return_value=sdk_graph),
            patch.object(runner, "analyze_request", side_effect=analyze),
            patch.object(runner, "guard_output", new=AsyncMock(return_value=guarded)),
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
        self.assertEqual(
            {"inputTokens": 3, "outputTokens": 2, "totalTokens": 5},
            completed["ext"]["analysisUsage"],
        )
        self.assertEqual(revised, result["finalOutput"])
        self.assertEqual(revised, result["outputs"][0]["text"])
        self.assertEqual("guarded", result["artifacts"][0]["artifactCode"])
        self.assertEqual(
            {"inputTokens": 14, "outputTokens": 9, "totalTokens": 23},
            result["usage"],
        )
        self.assertEqual(
            {
                "status": "DEGRADED",
                "durationMs": 7,
                "usage": {"inputTokens": 3, "outputTokens": 2, "totalTokens": 5},
            },
            result["providerMeta"]["requestAnalysis"],
        )

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

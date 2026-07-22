import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from agent_provider.runtime.confidence_guard import (
    ConfidenceAssessment,
    ConfidencePolicy,
    EvidencePlan,
    KnowledgeEvidenceCollector,
    _assessment_input,
    _evidence_text,
    _grounded_confidence,
    _merge_evidence,
    guard_output,
)


class EventRecorder:
    def __init__(self) -> None:
        self.events: list[dict[str, object]] = []

    def event(self, event_type: str, **kwargs: object) -> None:
        self.events.append({"eventType": event_type, **kwargs})


def policy(*, audit_enabled: bool = True, max_retries: int = 1) -> ConfidencePolicy:
    return ConfidencePolicy(
        enabled=True,
        scoring_enabled=True,
        threshold=0.9,
        retrieval_enabled=True,
        retrieval_top_k=5,
        reanalysis_enabled=True,
        max_retries=max_retries,
        audit_enabled=audit_enabled,
    )


def graph() -> SimpleNamespace:
    return SimpleNamespace(
        payload={
            "run": {
                "context": {
                    "knowledgeBases": [{"kbCode": "db-schema", "name": "数据库结构"}]
                }
            }
        },
        max_turns=3,
    )


def evidence(*items: dict[str, object]) -> dict[str, object]:
    return {
        "success": True,
        "kbCode": "db-schema",
        "query": "用户表字段",
        "items": list(items) or [
            {
                "documentId": "doc-1",
                "score": 0.87,
                "content": "user 表字段：id bigint",
            }
        ],
    }


class ConfidencePolicyTest(unittest.TestCase):
    def test_reads_java_default_policy(self) -> None:
        value = ConfidencePolicy.from_payload(
            {
                "confidencePolicy": {
                    "enabled": True,
                    "threshold": 0.9,
                    "scoring": {"enabled": True},
                    "retrieval": {"enabled": True, "topK": 5},
                    "reanalysis": {"enabled": True},
                    "maxRetries": 1,
                    "audit": {"enabled": True},
                }
            }
        )

        self.assertTrue(value.requires_guard)
        self.assertEqual(0.9, value.threshold)
        self.assertEqual(5, value.retrieval_top_k)
        self.assertEqual(1, value.max_retries)

    def test_is_disabled_without_a_java_policy(self) -> None:
        self.assertFalse(ConfidencePolicy.from_payload({}).requires_guard)


class ConfidenceScoringTest(unittest.TestCase):
    def test_assessment_input_contains_retrieved_evidence(self) -> None:
        value = _assessment_input(
            "查询用户表字段",
            "用户表包含 id 字段",
            [{"kbCode": "db-schema"}],
            evidence(),
        )

        retrieved = value["retrievedEvidence"]
        self.assertTrue(retrieved["available"])
        self.assertEqual("用户表字段", retrieved["query"])
        self.assertEqual("user 表字段：id bigint", retrieved["items"][0]["content"])

    def test_grounded_confidence_uses_only_explainable_dimensions(self) -> None:
        score = _grounded_confidence(
            evidence_coverage=0.95,
            evidence_consistency=1.0,
            answer_completeness=0.6,
            evidence=evidence(),
        )

        self.assertAlmostEqual(0.9375, score)

    def test_confidence_without_evidence_is_unscorable(self) -> None:
        score = _grounded_confidence(
            evidence_coverage=0.95,
            evidence_consistency=1.0,
            answer_completeness=0.8,
            evidence=None,
        )

        self.assertIsNone(score)

    def test_missing_hits_or_dimensions_is_unscorable(self) -> None:
        without_hits = _grounded_confidence(1.0, 1.0, 1.0, {"success": True, "items": []})
        without_dimensions = _grounded_confidence(None, None, None, evidence())

        self.assertIsNone(without_hits)
        self.assertIsNone(without_dimensions)

    def test_low_answer_completeness_is_unscorable(self) -> None:
        self.assertIsNone(_grounded_confidence(1.0, 1.0, 0.49, evidence()))

    def test_merge_evidence_preserves_and_deduplicates_previous_hits(self) -> None:
        merged = _merge_evidence(
            evidence({"documentId": "doc-1", "content": "user 表"}),
            {
                "success": True,
                "kbCode": "db-schema",
                "query": "用户表字段详情",
                "items": [
                    {"documentId": "doc-1", "content": "user 表"},
                    {"documentId": "doc-2", "content": "id bigint"},
                ],
            },
        )

        self.assertIsNotNone(merged)
        self.assertEqual(["用户表字段", "用户表字段详情"], merged["queries"])
        self.assertEqual(2, len(merged["items"]))

    def test_compact_evidence_stays_within_the_prompt_budget(self) -> None:
        value = {
            "success": True,
            "query": "q" * 2_000,
            "queries": ["q" * 2_000 for _ in range(10)],
            "items": [
                {
                    "documentId": f"doc-{index}",
                    "score": 1 - index / 100,
                    "content": "x" * 10_000,
                    "metadata": {"raw": "m" * 5_000},
                }
                for index in range(10)
            ],
        }

        self.assertLess(len(_evidence_text(value)), 24_000)

    def test_merge_evidence_ignores_items_from_a_failed_search(self) -> None:
        merged = _merge_evidence(
            None,
            {"success": False, "items": [{"documentId": "doc-1", "content": "不可用内容"}]},
        )

        self.assertIsNotNone(merged)
        self.assertEqual([], merged["items"])


class KnowledgeEvidenceCollectorTest(unittest.TestCase):
    def test_reuses_kb_tool_output_even_when_completed_item_has_only_call_id(self) -> None:
        collector = KnowledgeEvidenceCollector()
        collector.observe(
            "tool.started",
            {"callId": "call-1", "toolCode": "knowledge_base_search_tool"},
            SimpleNamespace(),
        )
        collector.observe(
            "tool.completed",
            {"callId": "call-1"},
            SimpleNamespace(
                raw_item=SimpleNamespace(type="function_call_output", call_id="call-1"),
                output=evidence(),
            ),
        )

        self.assertIsNotNone(collector.evidence)
        self.assertEqual("db-schema", collector.evidence["kbCode"])
        self.assertEqual(1, len(collector.evidence["items"]))

    def test_ignores_non_knowledge_tools(self) -> None:
        collector = KnowledgeEvidenceCollector()
        collector.observe(
            "tool.started",
            {"callId": "call-2", "toolCode": "data_preview_query_tool"},
            SimpleNamespace(),
        )
        collector.observe(
            "tool.completed",
            {"callId": "call-2"},
            SimpleNamespace(output=evidence()),
        )

        self.assertIsNone(collector.evidence)


class ConfidenceGuardLifecycleTest(unittest.IsolatedAsyncioTestCase):
    async def test_reuses_main_tool_evidence_and_exposes_only_final_score(self) -> None:
        recorder = EventRecorder()
        collected = evidence()
        sufficient = EvidencePlan(True, True, "db-schema", "用户表字段", "证据覆盖当前回答范围。")
        final = ConfidenceAssessment(0.94, 0.9, 1.0, 0.85)

        with (
            patch(
                "agent_provider.runtime.confidence_guard._plan_evidence",
                new=AsyncMock(return_value=sufficient),
            ),
            patch(
                "agent_provider.runtime.confidence_guard._assess",
                new=AsyncMock(return_value=final),
            ) as assess,
            patch(
                "agent_provider.runtime.confidence_guard.search_authorized_knowledge_base",
            ) as search,
            patch(
                "agent_provider.runtime.confidence_guard._reanalyze",
                new=AsyncMock(),
            ) as reanalyze,
        ):
            result = await guard_output(
                sdk_agent=object(),
                compiled_agent=SimpleNamespace(code="schema-agent", model="test-model"),
                graph=graph(),
                emitter=recorder,
                original_task="查询用户表字段",
                initial_output="原回答",
                policy=policy(),
                initial_evidence=collected,
            )

        self.assertEqual(0.94, result.confidence)
        self.assertEqual("SCORED", result.score_status)
        self.assertEqual(1, result.evidence_count)
        search.assert_not_called()
        reanalyze.assert_not_awaited()
        self.assertIs(collected, assess.await_args.kwargs["evidence"])
        self.assertEqual(
            [
                "confidence.evidence_check.started",
                "confidence.evidence_check.completed",
                "confidence.assessment.started",
                "confidence.assessment.completed",
            ],
            [event["eventType"] for event in recorder.events],
        )
        scored_events = [
            event
            for event in recorder.events
            if "confidence" in event.get("ext", {})
        ]
        self.assertEqual(1, len(scored_events))
        self.assertEqual("confidence.assessment.completed", scored_events[0]["eventType"])

    async def test_supplements_reanalyzes_then_scores_once(self) -> None:
        recorder = EventRecorder()
        initial = evidence({"documentId": "doc-1", "content": "user 表存在"})
        supplement = evidence({"documentId": "doc-2", "content": "user 表字段：id bigint"})
        needs_more = EvidencePlan(True, False, "db-schema", "用户表字段", "字段证据尚不完整。")
        sufficient = EvidencePlan(True, True, "db-schema", "用户表字段", "字段证据已覆盖回答。")
        final = ConfidenceAssessment(0.96, 0.95, 1.0, 0.825)

        with (
            patch(
                "agent_provider.runtime.confidence_guard._plan_evidence",
                new=AsyncMock(side_effect=[needs_more, sufficient, sufficient]),
            ),
            patch(
                "agent_provider.runtime.confidence_guard.search_authorized_knowledge_base",
                return_value=supplement,
            ) as search,
            patch(
                "agent_provider.runtime.confidence_guard._reanalyze",
                new=AsyncMock(return_value="基于完整证据的新回答"),
            ) as reanalyze,
            patch(
                "agent_provider.runtime.confidence_guard._assess",
                new=AsyncMock(return_value=final),
            ) as assess,
        ):
            result = await guard_output(
                sdk_agent=object(),
                compiled_agent=SimpleNamespace(code="schema-agent", model="test-model"),
                graph=graph(),
                emitter=recorder,
                original_task="查询用户表字段",
                initial_output="原回答",
                policy=policy(max_retries=3),
                initial_evidence=initial,
            )

        self.assertEqual("基于完整证据的新回答", result.text)
        self.assertEqual(0.96, result.confidence)
        self.assertEqual(1, result.retrieval_attempts)
        self.assertEqual(1, result.reanalysis_attempts)
        self.assertEqual(2, result.evidence_count)
        search.assert_called_once()
        reanalyze.assert_awaited_once()
        final_evidence = assess.await_args.kwargs["evidence"]
        self.assertEqual(2, len(final_evidence["items"]))
        event_types = [event["eventType"] for event in recorder.events]
        self.assertLess(event_types.index("confidence.retrieval.completed"), event_types.index("confidence.reanalysis.started"))
        self.assertLess(event_types.index("confidence.reanalysis.completed"), event_types.index("confidence.assessment.started"))
        for event in recorder.events:
            if event["eventType"] != "confidence.assessment.completed":
                self.assertNotIn("confidence", event.get("ext", {}))

    async def test_missing_final_dimensions_emit_skipped_instead_of_completed(self) -> None:
        recorder = EventRecorder()
        collected = evidence()
        sufficient = EvidencePlan(True, True, "db-schema", "用户表字段", "证据数量充分。")
        unscorable = ConfidenceAssessment(None, 0.9, 1.0, 0.4)

        with (
            patch(
                "agent_provider.runtime.confidence_guard._plan_evidence",
                new=AsyncMock(return_value=sufficient),
            ),
            patch(
                "agent_provider.runtime.confidence_guard._assess",
                new=AsyncMock(return_value=unscorable),
            ),
        ):
            result = await guard_output(
                sdk_agent=object(),
                compiled_agent=SimpleNamespace(code="schema-agent", model="test-model"),
                graph=graph(),
                emitter=recorder,
                original_task="查询用户表字段",
                initial_output="原回答",
                policy=policy(),
                initial_evidence=collected,
            )

        self.assertIsNone(result.confidence)
        self.assertEqual("INSUFFICIENT_EVIDENCE", result.score_status)
        event_types = [event["eventType"] for event in recorder.events]
        self.assertIn("confidence.assessment.started", event_types)
        self.assertNotIn("confidence.assessment.completed", event_types)
        skipped = recorder.events[-1]
        self.assertEqual("confidence.assessment.skipped", skipped["eventType"])
        self.assertNotIn("confidence", skipped["ext"])
        self.assertEqual("INSUFFICIENT_EVIDENCE", skipped["ext"]["scoreStatus"])

    async def test_zero_retries_does_not_retrieve_or_reanalyze_existing_evidence(self) -> None:
        recorder = EventRecorder()
        insufficient = EvidencePlan(
            True,
            False,
            "db-schema",
            "用户表字段",
            "现有证据不足以覆盖字段范围。",
        )

        with (
            patch(
                "agent_provider.runtime.confidence_guard._plan_evidence",
                new=AsyncMock(return_value=insufficient),
            ),
            patch(
                "agent_provider.runtime.confidence_guard.search_authorized_knowledge_base",
            ) as search,
            patch(
                "agent_provider.runtime.confidence_guard._reanalyze",
                new=AsyncMock(),
            ) as reanalyze,
            patch(
                "agent_provider.runtime.confidence_guard._assess",
                new=AsyncMock(),
            ) as assess,
        ):
            result = await guard_output(
                sdk_agent=object(),
                compiled_agent=SimpleNamespace(code="schema-agent", model="test-model"),
                graph=graph(),
                emitter=recorder,
                original_task="查询用户表字段",
                initial_output="原回答",
                policy=policy(max_retries=0),
                initial_evidence=evidence(),
            )

        self.assertEqual("INSUFFICIENT_EVIDENCE", result.score_status)
        self.assertEqual(0, result.retrieval_attempts)
        self.assertEqual(0, result.reanalysis_attempts)
        search.assert_not_called()
        reanalyze.assert_not_awaited()
        assess.assert_not_awaited()

    async def test_empty_retrieval_is_unscorable_without_percentage(self) -> None:
        recorder = EventRecorder()
        needs_more = EvidencePlan(True, False, "db-schema", "用户表字段", "没有有效字段证据。")

        with (
            patch(
                "agent_provider.runtime.confidence_guard._plan_evidence",
                new=AsyncMock(side_effect=[needs_more, needs_more]),
            ),
            patch(
                "agent_provider.runtime.confidence_guard.search_authorized_knowledge_base",
                return_value={"success": True, "kbCode": "db-schema", "items": []},
            ),
            patch(
                "agent_provider.runtime.confidence_guard._assess",
                new=AsyncMock(),
            ) as assess,
            patch(
                "agent_provider.runtime.confidence_guard._reanalyze",
                new=AsyncMock(),
            ) as reanalyze,
        ):
            result = await guard_output(
                sdk_agent=object(),
                compiled_agent=SimpleNamespace(code="schema-agent", model="test-model"),
                graph=graph(),
                emitter=recorder,
                original_task="查询用户表字段",
                initial_output="原回答",
                policy=policy(),
            )

        self.assertIsNone(result.confidence)
        self.assertEqual("INSUFFICIENT_EVIDENCE", result.score_status)
        assess.assert_not_awaited()
        reanalyze.assert_not_awaited()
        skipped = next(
            event for event in recorder.events
            if event["eventType"] == "confidence.assessment.skipped"
        )
        self.assertNotIn("confidence", skipped["ext"])
        self.assertIn("暂不评分", skipped["ext"]["outputSummary"])

    async def test_retrieval_exception_preserves_answer_but_not_an_ungrounded_score(self) -> None:
        recorder = EventRecorder()
        needs_more = EvidencePlan(True, False, "db-schema", "用户表字段", "需要知识库证据。")

        with (
            patch(
                "agent_provider.runtime.confidence_guard._plan_evidence",
                new=AsyncMock(side_effect=[needs_more, needs_more]),
            ),
            patch(
                "agent_provider.runtime.confidence_guard.search_authorized_knowledge_base",
                side_effect=TimeoutError("timed out"),
            ),
        ):
            result = await guard_output(
                sdk_agent=object(),
                compiled_agent=SimpleNamespace(code="schema-agent", model="test-model"),
                graph=graph(),
                emitter=recorder,
                original_task="查询用户表字段",
                initial_output="原回答",
                policy=policy(),
            )

        self.assertEqual("原回答", result.text)
        self.assertIsNone(result.confidence)
        retrieval = next(
            event for event in recorder.events
            if event["eventType"] == "confidence.retrieval.completed"
        )
        self.assertEqual("FAILED", retrieval["status"])
        self.assertNotIn("confidence", retrieval["ext"])

    async def test_non_factual_task_is_not_scored(self) -> None:
        recorder = EventRecorder()
        not_applicable = EvidencePlan(False, False, None, "", "这是创意改写任务。")

        with patch(
            "agent_provider.runtime.confidence_guard._plan_evidence",
            new=AsyncMock(return_value=not_applicable),
        ):
            result = await guard_output(
                sdk_agent=object(),
                compiled_agent=SimpleNamespace(code="writer", model="test-model"),
                graph=graph(),
                emitter=recorder,
                original_task="润色这句话",
                initial_output="润色后的回答",
                policy=policy(),
            )

        self.assertIsNone(result.confidence)
        self.assertEqual("NOT_APPLICABLE", result.score_status)
        self.assertEqual("confidence.assessment.skipped", recorder.events[-1]["eventType"])


if __name__ == "__main__":
    unittest.main()

import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from agent_provider.runtime.confidence_guard import (
    ConfidenceAssessment,
    ConfidencePolicy,
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


class ConfidencePolicyTest(unittest.TestCase):
    def test_reads_java_default_policy(self) -> None:
        policy = ConfidencePolicy.from_payload(
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

        self.assertTrue(policy.requires_guard)
        self.assertEqual(0.9, policy.threshold)
        self.assertEqual(5, policy.retrieval_top_k)
        self.assertEqual(1, policy.max_retries)

    def test_is_disabled_without_a_java_policy(self) -> None:
        policy = ConfidencePolicy.from_payload({})

        self.assertFalse(policy.requires_guard)


class ConfidenceScoringTest(unittest.TestCase):
    def test_assessment_input_contains_retrieved_evidence(self) -> None:
        value = _assessment_input(
            "查询用户表字段",
            "用户表包含 id 字段",
            [{"kbCode": "db-schema"}],
            {
                "success": True,
                "kbCode": "db-schema",
                "query": "用户表字段",
                "items": [{
                    "documentId": "doc-1",
                    "score": 0.87,
                    "content": "user 表字段：id bigint",
                    "metadata": {"source": "schema"},
                }],
            },
        )

        evidence = value["retrievedEvidence"]
        self.assertTrue(evidence["available"])
        self.assertEqual("用户表字段", evidence["query"])
        self.assertEqual("user 表字段：id bigint", evidence["items"][0]["content"])

    def test_grounded_confidence_uses_explainable_dimensions(self) -> None:
        score = _grounded_confidence(
            0.12,
            evidence_coverage=0.95,
            evidence_consistency=1.0,
            answer_completeness=0.6,
            evidence={"success": True, "items": [{"content": "明确字段定义"}]},
        )

        self.assertAlmostEqual(0.9375, score)

    def test_confidence_without_evidence_keeps_evaluator_score(self) -> None:
        score = _grounded_confidence(
            0.42,
            evidence_coverage=0.0,
            evidence_consistency=0.0,
            answer_completeness=0.8,
            evidence=None,
        )

        self.assertEqual(0.42, score)

    def test_grounded_confidence_fails_closed_without_hits_or_dimensions(self) -> None:
        without_hits = _grounded_confidence(
            0.99,
            evidence_coverage=1.0,
            evidence_consistency=1.0,
            answer_completeness=1.0,
            evidence={"success": True, "items": []},
        )
        without_dimensions = _grounded_confidence(
            0.99,
            evidence_coverage=None,
            evidence_consistency=None,
            answer_completeness=None,
            evidence={"success": True, "items": [{"content": "明确字段定义"}]},
        )

        self.assertEqual(0.0, without_hits)
        self.assertEqual(0.0, without_dimensions)

    def test_grounded_confidence_requires_a_minimum_answer_completeness(self) -> None:
        score = _grounded_confidence(
            0.99,
            evidence_coverage=1.0,
            evidence_consistency=1.0,
            answer_completeness=0.49,
            evidence={"success": True, "items": [{"content": "明确字段定义"}]},
        )

        self.assertEqual(0.0, score)

    def test_merge_evidence_preserves_and_deduplicates_previous_hits(self) -> None:
        merged = _merge_evidence(
            {
                "success": True,
                "kbCode": "db-schema",
                "query": "用户表",
                "items": [{"documentId": "doc-1", "content": "user 表"}],
            },
            {
                "success": True,
                "kbCode": "db-schema",
                "query": "用户表字段",
                "items": [
                    {"documentId": "doc-1", "content": "user 表"},
                    {"documentId": "doc-2", "content": "id bigint"},
                ],
            },
        )

        self.assertIsNotNone(merged)
        self.assertEqual(["用户表", "用户表字段"], merged["queries"])
        self.assertEqual(2, len(merged["items"]))

    def test_compact_evidence_stays_within_the_prompt_budget(self) -> None:
        evidence = {
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

        serialized = _evidence_text(evidence)

        self.assertLess(len(serialized), 24_000)

    def test_merge_evidence_ignores_items_from_a_failed_search(self) -> None:
        merged = _merge_evidence(
            None,
            {"success": False, "items": [{"documentId": "doc-1", "content": "不可用内容"}]},
        )

        self.assertIsNotNone(merged)
        self.assertEqual([], merged["items"])


class ConfidenceGuardLifecycleTest(unittest.IsolatedAsyncioTestCase):
    async def test_candidates_are_compared_against_the_same_evidence(self) -> None:
        policy = ConfidencePolicy(
            enabled=True,
            scoring_enabled=True,
            threshold=0.9,
            retrieval_enabled=True,
            retrieval_top_k=5,
            reanalysis_enabled=True,
            max_retries=1,
            audit_enabled=False,
        )
        initial = ConfidenceAssessment(0.8, "db-schema", "用户表字段")
        grounded_original = ConfidenceAssessment(
            0.2,
            "db-schema",
            "用户表字段",
            evidence_coverage=0.1,
            evidence_consistency=0.2,
            answer_completeness=0.6,
        )
        grounded_revision = ConfidenceAssessment(
            0.3,
            "db-schema",
            "用户表字段",
            evidence_coverage=0.2,
            evidence_consistency=0.3,
            answer_completeness=0.6,
        )
        graph = SimpleNamespace(
            payload={"run": {"context": {"knowledgeBases": [{"kbCode": "db-schema"}]}}},
            max_turns=3,
        )
        evidence = {
            "success": True,
            "kbCode": "db-schema",
            "items": [{"documentId": "doc-1", "content": "user 表字段：id bigint"}],
        }

        with (
            patch(
                "agent_provider.runtime.confidence_guard._assess",
                new=AsyncMock(side_effect=[initial, grounded_original, grounded_revision]),
            ) as assess,
            patch(
                "agent_provider.runtime.confidence_guard._reanalyze",
                new=AsyncMock(return_value="有证据但仍不完整的新回答"),
            ),
            patch(
                "agent_provider.runtime.confidence_guard.search_authorized_knowledge_base",
                return_value=evidence,
            ),
        ):
            result = await guard_output(
                sdk_agent=object(),
                compiled_agent=SimpleNamespace(code="schema-agent", model="test-model"),
                graph=graph,
                emitter=object(),
                original_task="查询用户表字段",
                initial_output="原回答",
                policy=policy,
            )

        self.assertEqual("有证据但仍不完整的新回答", result.text)
        self.assertEqual(0.3, result.confidence)
        self.assertEqual(1, result.reanalysis_attempts)
        self.assertEqual(1, result.retrieval_attempts)
        self.assertEqual(3, assess.await_count)
        original_evidence = assess.await_args_list[1].kwargs["evidence"]
        revision_evidence = assess.await_args_list[2].kwargs["evidence"]
        self.assertIs(original_evidence, revision_evidence)
        self.assertEqual("用户表字段", original_evidence["query"])
        self.assertEqual("user 表字段：id bigint", original_evidence["items"][0]["content"])

    async def test_empty_retrieval_cannot_trigger_an_ungrounded_high_score(self) -> None:
        policy = ConfidencePolicy(True, True, 0.9, True, 5, True, 3, False)
        initial = ConfidenceAssessment(0.4, "db-schema", "用户表字段")
        graph = SimpleNamespace(
            payload={"run": {"context": {"knowledgeBases": [{"kbCode": "db-schema"}]}}},
            max_turns=3,
        )
        reanalyze = AsyncMock(return_value="未经证据支持的润色回答")

        with (
            patch(
                "agent_provider.runtime.confidence_guard._assess",
                new=AsyncMock(return_value=initial),
            ) as assess,
            patch("agent_provider.runtime.confidence_guard._reanalyze", new=reanalyze),
            patch(
                "agent_provider.runtime.confidence_guard.search_authorized_knowledge_base",
                return_value={"success": True, "kbCode": "db-schema", "items": []},
            ),
        ):
            result = await guard_output(
                sdk_agent=object(),
                compiled_agent=SimpleNamespace(code="schema-agent", model="test-model"),
                graph=graph,
                emitter=object(),
                original_task="查询用户表字段",
                initial_output="原回答",
                policy=policy,
            )

        self.assertEqual("原回答", result.text)
        self.assertEqual(0.4, result.confidence)
        self.assertEqual(1, assess.await_count)
        reanalyze.assert_not_awaited()

    async def test_retrieval_exception_does_not_fail_the_completed_answer(self) -> None:
        policy = ConfidencePolicy(True, True, 0.9, True, 5, True, 3, True)
        initial = ConfidenceAssessment(0.4, "db-schema", "用户表字段")
        graph = SimpleNamespace(
            payload={"run": {"context": {"knowledgeBases": [{"kbCode": "db-schema"}]}}},
            max_turns=3,
        )
        emitter = EventRecorder()

        with (
            patch(
                "agent_provider.runtime.confidence_guard._assess",
                new=AsyncMock(return_value=initial),
            ),
            patch("agent_provider.runtime.confidence_guard._reanalyze", new=AsyncMock()) as reanalyze,
            patch(
                "agent_provider.runtime.confidence_guard.search_authorized_knowledge_base",
                side_effect=TimeoutError("timed out"),
            ),
        ):
            result = await guard_output(
                sdk_agent=object(),
                compiled_agent=SimpleNamespace(code="schema-agent", model="test-model"),
                graph=graph,
                emitter=emitter,
                original_task="查询用户表字段",
                initial_output="原回答",
                policy=policy,
            )

        self.assertEqual("原回答", result.text)
        self.assertEqual(0.4, result.confidence)
        self.assertEqual(1, result.retrieval_attempts)
        self.assertEqual(0, result.reanalysis_attempts)
        reanalyze.assert_not_awaited()
        retrieval_events = [
            event for event in emitter.events
            if event["eventType"] == "confidence.retrieval.completed"
        ]
        self.assertEqual("FAILED", retrieval_events[0]["status"])


if __name__ == "__main__":
    unittest.main()

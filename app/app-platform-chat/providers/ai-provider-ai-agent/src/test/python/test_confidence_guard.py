import unittest
from types import SimpleNamespace
from unittest.mock import AsyncMock, patch

from agent_provider.runtime.confidence_guard import (
    ConfidenceAssessment,
    ConfidencePolicy,
    _assessment_input,
    _grounded_confidence,
    guard_output,
)


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


class ConfidenceGuardLifecycleTest(unittest.IsolatedAsyncioTestCase):
    async def test_reassessment_receives_evidence_and_does_not_replace_a_better_answer(self) -> None:
        policy = ConfidencePolicy(
            enabled=True,
            scoring_enabled=True,
            threshold=0.9,
            retrieval_enabled=True,
            retrieval_top_k=5,
            reanalysis_enabled=True,
            max_retries=3,
            audit_enabled=False,
        )
        initial = ConfidenceAssessment(0.4, "db-schema", "用户表字段")
        regressed = ConfidenceAssessment(
            0.3,
            "db-schema",
            "用户表字段",
            evidence_coverage=0.3,
            evidence_consistency=0.4,
            answer_completeness=0.3,
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
                new=AsyncMock(side_effect=[initial, regressed]),
            ) as assess,
            patch(
                "agent_provider.runtime.confidence_guard._reanalyze",
                new=AsyncMock(return_value="较低评分的新回答"),
            ),
            patch(
                "agent_provider.runtime.confidence_guard.search_authorized_knowledge_base",
                return_value=evidence,
            ),
        ):
            result = await guard_output(
                sdk_agent=object(),
                compiled_agent=SimpleNamespace(model="test-model"),
                graph=graph,
                emitter=object(),
                original_task="查询用户表字段",
                initial_output="原回答",
                policy=policy,
            )

        self.assertEqual("原回答", result.text)
        self.assertEqual(0.4, result.confidence)
        self.assertEqual(1, result.reanalysis_attempts)
        self.assertEqual(1, result.retrieval_attempts)
        self.assertEqual(2, assess.await_count)
        reassessment_evidence = assess.await_args_list[1].kwargs["evidence"]
        self.assertEqual("用户表字段", reassessment_evidence["query"])
        self.assertEqual("user 表字段：id bigint", reassessment_evidence["items"][0]["content"])


if __name__ == "__main__":
    unittest.main()

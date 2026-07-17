import unittest

from agent_provider.runtime.confidence_guard import ConfidencePolicy


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


if __name__ == "__main__":
    unittest.main()

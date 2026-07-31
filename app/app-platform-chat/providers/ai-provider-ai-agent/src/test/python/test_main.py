import io
import os
import unittest
from contextlib import redirect_stderr
from unittest.mock import patch

from agent_provider.main import _log_startup_environment


class MainStartupEnvironmentTest(unittest.TestCase):
    def test_logs_environment_presence_without_exposing_credentials(self) -> None:
        stderr = io.StringIO()
        with patch.dict(
            os.environ,
            {
                "OPENAI_API_KEY": "secret-openai-key",
                "OPENAI_MODEL": "gpt-test",
                "AI_AGENT_PLATFORM_TOKEN": "temporary-platform-token",
                "AI_AGENT_DATA_PREVIEW_TOKEN": "",
            },
            clear=True,
        ), redirect_stderr(stderr):
            _log_startup_environment()

        output = stderr.getvalue()
        self.assertIn(
            "AI_AGENT_ENV key=AI_AGENT_PLATFORM_TOKEN present=true nonEmpty=true "
            "length=24 value=[REDACTED]",
            output,
        )
        self.assertIn(
            "AI_AGENT_ENV key=AI_AGENT_DATA_PREVIEW_TOKEN present=true nonEmpty=false "
            "length=0 value=<empty>",
            output,
        )
        self.assertIn(
            "AI_AGENT_ENV key=AI_AGENT_KB_SEARCH_TOKEN present=false nonEmpty=false "
            "length=0 value=<missing>",
            output,
        )
        self.assertIn("AI_AGENT_ENV key=OPENAI_MODEL", output)
        self.assertIn("value=gpt-test", output)
        self.assertNotIn("secret-openai-key", output)
        self.assertNotIn("temporary-platform-token", output)


if __name__ == "__main__":
    unittest.main()

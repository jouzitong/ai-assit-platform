package ai.platform.aiassit.agent.runtime.tool;

import ai.platform.aiassit.service.ai.spi.tool.ManagedToolExecutionRequest;
import ai.platform.aiassit.service.ai.spi.tool.ManagedToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedToolProcessExecutorTest {

    private final ManagedToolProcessExecutor executor = new ManagedToolProcessExecutor(
            new ObjectMapper(), "python3", "node");

    @Test
    void executesPythonEntrypointWithConfigAndTemporaryTokenEnvironment() {
        Map<String, Object> definition = definition("PYTHON", """
                import os

                async def run(arguments, context):
                    print("python-log")
                    return {
                        "query": arguments.get("query"),
                        "region": context.get("config", {}).get("region"),
                        "hasToken": bool(os.getenv("AI_AGENT_KB_SEARCH_TOKEN")),
                    }
                """);

        ManagedToolExecutionResult result = executor.execute(ManagedToolExecutionRequest.builder()
                .definition(definition)
                .arguments(Map.of("query", "orders"))
                .context(Map.of("config", Map.of("region", "cn")))
                .executionToken("temporary-token")
                .build());

        assertThat(result.getOutput()).isEqualTo(Map.of(
                "query", "orders", "region", "cn", "hasToken", true));
        assertThat(result.getStdout()).contains("python-log");
    }

    @Test
    void executesJavascriptEntrypoint() {
        Map<String, Object> definition = definition("JAVASCRIPT", """
                export async function run(args, context) {
                  return { value: args.value, configured: context.config.enabled };
                }
                """);

        ManagedToolExecutionResult result = executor.execute(ManagedToolExecutionRequest.builder()
                .definition(definition)
                .arguments(Map.of("value", 7))
                .context(Map.of("config", Map.of("enabled", true)))
                .build());

        assertThat(result.getOutput()).isEqualTo(Map.of("value", 7, "configured", true));
    }

    private Map<String, Object> definition(String runtime, String source) {
        return Map.of(
                "executionMode", "MANAGED_CODE",
                "implementationRuntime", runtime,
                "compatibleAgentRuntimes", List.of("OPENAI_AGENTS_PYTHON", "OPENAI_AGENTS_TYPESCRIPT"),
                "sourceCode", source,
                "runtimeConfig", Map.of(),
                "timeoutMs", 10_000);
    }
}

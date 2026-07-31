package ai.platform.aiassit.agent.runtime.tool;

import ai.platform.aiassit.service.ai.spi.tool.ManagedToolExecutionRequest;
import ai.platform.aiassit.service.ai.spi.tool.ManagedToolExecutionResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.arthena.framework.common.thread.DefaultAsyncTaskExcutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ManagedToolProcessExecutorTest {

    private final DefaultAsyncTaskExcutor asyncTaskExcutor = new DefaultAsyncTaskExcutor();
    private final ManagedToolProcessExecutor executor = new ManagedToolProcessExecutor(
            new ObjectMapper(), "python3", "node", asyncTaskExcutor);

    @AfterEach
    void tearDown() {
        asyncTaskExcutor.close();
    }

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

    @Test
    void executesAndDescribesPythonAgentsSdkFunctionTool() {
        Map<String, Object> definition = definition("PYTHON", """
                import os
                from agents import function_tool

                @function_tool
                async def knowledge_base_search_tool(query: str, top_k: int = 5) -> dict:
                    \"\"\"Search the knowledge base.\"\"\"
                    return {
                        \"query\": query,
                        \"topK\": top_k,
                        \"hasToken\": bool(os.getenv(\"AI_AGENT_KB_SEARCH_TOKEN\")),
                    }
                """);

        Map<String, Object> metadata = executor.describe(definition);
        ManagedToolExecutionResult result = executor.execute(ManagedToolExecutionRequest.builder()
                .definition(definition)
                .arguments(Map.of("query", "orders", "top_k", 3))
                .executionToken("temporary-token")
                .build());

        assertThat(metadata).containsEntry("name", "knowledge_base_search_tool");
        assertThat(((Map<?, ?>) metadata.get("inputSchema")).get("type")).isEqualTo("object");
        assertThat(result.getOutput()).isEqualTo(Map.of(
                "query", "orders", "topK", 3, "hasToken", true));
    }

    @Test
    void executesAndDescribesJavascriptAgentsSdkTool() {
        Map<String, Object> definition = definition("JAVASCRIPT", """
                import { tool } from "@openai/agents";

                export const lookupTool = tool({
                  name: "lookup_tool",
                  description: "Look up one value.",
                  parameters: {
                    type: "object",
                    properties: { value: { type: "integer" } },
                    required: ["value"],
                    additionalProperties: false,
                  },
                  async execute({ value }) {
                    return { doubled: value * 2 };
                  },
                });
                """);

        Map<String, Object> metadata = executor.describe(definition);
        ManagedToolExecutionResult result = executor.execute(ManagedToolExecutionRequest.builder()
                .definition(definition)
                .arguments(Map.of("value", 7))
                .build());

        assertThat(metadata).containsEntry("name", "lookup_tool");
        assertThat(((Map<?, ?>) metadata.get("inputSchema")).get("type")).isEqualTo("object");
        assertThat(result.getOutput()).isEqualTo(Map.of("doubled", 14));
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

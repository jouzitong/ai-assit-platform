package ai.platform.aiassit.agent.runtime;

import ai.platform.aiassit.service.ai.spi.agent.AgentDefinitionStore;
import ai.platform.aiassit.service.ai.spi.agent.AgentEntrySummary;
import ai.platform.aiassit.service.ai.spi.agent.AgentRuntimeType;
import ai.platform.aiassit.service.ai.spi.agent.StoredAgentDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Bootstrap definitions used before an installation publishes database-backed versions. */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class SeedAgentDefinitionStore implements AgentDefinitionStore {

    private static final String HOME = "home-assistant";
    private final Map<String, StoredAgentDefinition> definitions = new LinkedHashMap<>();

    public SeedAgentDefinitionStore(ObjectMapper objectMapper) {
        definitions.put(HOME, definition(objectMapper, HOME, "首页智能助手",
                "理解用户目标，按需调用专业智能体，并负责最终答复。",
                "你是平台首页智能任务助手，也是唯一面向用户的答复所有者。"
                        + "先理解用户目标；问候、身份询问、常识问答或无需专业能力的问题必须直接用自然语言回答，禁止调用专业智能体。"
                        + "只有任务确实需要专业能力时，才把已配置的专业智能体作为工具调用，其返回仅作为内部材料。"
                        + "只有已经存在候选产出物且确实需要质量复核时，才调用 review_result。"
                        + "无论调用了哪些工具，最终都必须由你整合为清晰、可验证的 Markdown 答复；"
                        + "不得把专业智能体的结构化检查报告、工具原始返回或内部协议 JSON 原样展示给用户。",
                List.of(
                        collaborator("requirement-analyst", "analyze_requirement", "分析复杂需求和缺失信息"),
                        collaborator("sql-specialist", "plan_and_generate_sql", "规划数据查询并生成安全的候选 SQL"),
                        collaborator("render-specialist", "build_render", "生成可验收的 Render JSON 产出物"),
                        collaborator("result-reviewer", "review_result", "仅复核已经生成的候选产出物及其验收标准")
                )));
        definitions.put("requirement-analyst", specialist(objectMapper, "requirement-analyst", "需求分析智能体",
                "分析目标、约束、业务术语和缺失信息，返回结构化结论。"));
        definitions.put("sql-specialist", specialist(objectMapper, "sql-specialist", "SQL 专业智能体",
                "根据已授权的数据能力规划查询并生成只读、安全、可解释的候选 SQL。"));
        definitions.put("render-specialist", specialist(objectMapper, "render-specialist", "渲染专业智能体",
                "根据用户目标和已确认数据生成符合 Render JSON 契约的产出物。"));
        definitions.put("result-reviewer", specialist(objectMapper, "result-reviewer", "结果复核智能体",
                "你是仅供上层 Agent 调用的结果复核智能体。输入必须包含待复核候选产出物及其验收标准；"
                        + "如果没有候选产出物，明确返回不可复核，不回答原始用户问题。"
                        + "只输出包含结论、问题和改进建议的结构化检查报告，不静默修改被检查的产出物，"
                        + "也不承担面向用户的最终答复。"));
    }

    @Override
    public Optional<StoredAgentDefinition> resolve(String agentCode, Integer version) {
        StoredAgentDefinition definition = definitions.get(agentCode);
        if (definition == null || (version != null && !version.equals(definition.getAgentVersion()))) {
            return Optional.empty();
        }
        return Optional.of(definition);
    }

    @Override
    public Optional<StoredAgentDefinition> resolveEntry(String entryCode) {
        return "HOME_CHAT".equalsIgnoreCase(entryCode) || "SETTINGS_ASSISTANT".equalsIgnoreCase(entryCode)
                ? Optional.of(definitions.get(HOME))
                : Optional.empty();
    }

    @Override
    public List<AgentEntrySummary> listAvailable(String entryCode) {
        if (!"HOME_CHAT".equalsIgnoreCase(entryCode) && !"SETTINGS_ASSISTANT".equalsIgnoreCase(entryCode)) {
            return List.of();
        }
        StoredAgentDefinition home = definitions.get(HOME);
        return List.of(AgentEntrySummary.builder()
                .code(home.getAgentCode())
                .name(home.getName())
                .description(home.getDescription())
                .version(home.getAgentVersion())
                .build());
    }

    private StoredAgentDefinition specialist(ObjectMapper objectMapper, String code, String name, String instructions) {
        return definition(objectMapper, code, name, instructions, instructions, List.of());
    }

    private StoredAgentDefinition definition(ObjectMapper objectMapper,
                                             String code,
                                             String name,
                                             String description,
                                             String instructions,
                                             List<Map<String, Object>> collaborators) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("code", code);
        metadata.put("version", 1);
        metadata.put("name", name);
        metadata.put("description", description);
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("seed", "true");
        if (HOME.equals(code)) labels.put("entry", "HOME_CHAT");
        else labels.put("specialty", switch (code) {
            case "requirement-analyst" -> "requirement";
            case "result-reviewer" -> "review";
            default -> code.replace("-specialist", "");
        });
        metadata.put("labels", labels);

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("instructions", Map.of("type", "inline", "text", instructions));
        spec.put("model", Map.of("ref", "model://default-quality", "settings", Map.of("temperature", 0.2)));
        spec.put("toolRefs", List.of());
        spec.put("skillRefs", List.of());
        spec.put("knowledgeRefs", List.of());
        spec.put("mcpRefs", List.of());
        spec.put("collaboration", Map.of("agentTools", collaborators, "handoffs", List.of()));
        spec.put("guardrails", Map.of("input", List.of(), "output", List.of()));
        spec.put("runtimeDefaults", Map.of(
                "maxTurns", 12,
                "timeoutMs", 120_000,
                "maxAgentDepth", 4,
                "toolConcurrency", 4,
                "tracing", Map.of("enabled", true, "includeSensitiveData", false),
                "stateStrategy", "applicationReplay"
        ));
        if (HOME.equals(code)) {
            spec.put("output", Map.of(
                    "mode", "artifactSet",
                    "workflowRef", "workflow://home-chat-output/v1",
                    "schema", Map.of()));
        } else {
            spec.put("output", Map.of("mode", "text", "schema", Map.of()));
        }
        spec.put("extensions", Map.of());

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("apiVersion", "ai.platform/v1alpha1");
        manifest.put("kind", "Agent");
        manifest.put("metadata", metadata);
        manifest.put("spec", spec);
        try {
            return StoredAgentDefinition.builder()
                    .agentCode(code)
                    .agentVersion(1)
                    .name(name)
                    .description(description)
                    .manifestJson(objectMapper.writeValueAsString(manifest))
                    .runtimeType(AgentRuntimeType.OPENAI_AGENTS_PYTHON)
                    .sdkVersion("pinned")
                    .checksum(null)
                    .resolvedCapabilitiesJson("{}")
                    .workflowSnapshotJson(HOME.equals(code)
                            ? objectMapper.writeValueAsString(homeWorkflow())
                            : "{}")
                    .build();
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to create bootstrap Agent manifest", ex);
        }
    }

    private Map<String, Object> collaborator(String code, String toolName, String description) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("targetAgentRef", "agent://" + code + "/v1");
        value.put("mode", "AS_TOOL");
        value.put("toolName", toolName);
        value.put("description", description);
        return value;
    }

    private Map<String, Object> homeWorkflow() {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("code", "home-chat-output");
        metadata.put("version", 1);
        metadata.put("name", "首页回答验收规范");
        metadata.put("description", "确保首页 Agent 返回非空、可展示的最终回答");
        metadata.put("labels", Map.of("seed", "true"));

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("code", "final-answer");
        artifact.put("name", "最终回答");
        artifact.put("artifactType", "TEXT");
        artifact.put("contentFormat", "MARKDOWN");
        artifact.put("required", true);
        // The final answer is rendered through the assistant message channel. This artifact is validation-only.
        artifact.put("visible", false);
        artifact.put("inlineSchema", Map.of("type", "string"));

        Map<String, Object> check = new LinkedHashMap<>();
        check.put("code", "final-answer-schema");
        check.put("name", "最终回答结构检查");
        check.put("targetArtifact", "final-answer");
        check.put("checkerType", "JSON_SCHEMA");
        check.put("severity", "ERROR");
        check.put("blocking", true);
        check.put("retryable", true);
        check.put("config", Map.of());

        Map<String, Object> spec = new LinkedHashMap<>();
        spec.put("artifacts", List.of(artifact));
        spec.put("checks", List.of(check));
        spec.put("completionPolicy", Map.of(
                "requireAllRequiredArtifacts", true,
                "requireAllBlockingChecksPassed", true));
        spec.put("repairPolicy", Map.of("maxRepairAttempts", 1, "onExhausted", "INPUT_REQUIRED"));

        Map<String, Object> workflow = new LinkedHashMap<>();
        workflow.put("apiVersion", "ai.platform/v1alpha1");
        workflow.put("kind", "ArtifactWorkflow");
        workflow.put("metadata", metadata);
        workflow.put("spec", spec);
        workflow.put("workflowRef", "workflow://home-chat-output/v1");
        return workflow;
    }
}

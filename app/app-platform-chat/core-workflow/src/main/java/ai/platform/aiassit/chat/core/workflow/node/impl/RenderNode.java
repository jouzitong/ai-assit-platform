package ai.platform.aiassit.chat.core.workflow.node.impl;

import ai.platform.aiassit.service.ai.api.AiChatExecutionApi;
import ai.platform.aiassit.service.ai.api.dto.ChatMessage;
import ai.platform.aiassit.service.ai.api.dto.ChatOptions;
import ai.platform.aiassit.service.ai.api.dto.ChatRequest;
import ai.platform.aiassit.service.ai.api.dto.ChatResponse;
import ai.platform.aiassit.service.ai.api.dto.OutputItem;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.dto.ResponseFormat;
import ai.platform.aiassit.service.ai.api.dto.ToolDefinition;
import ai.platform.aiassit.service.ai.api.enums.MessageRole;
import ai.platform.aiassit.service.ai.api.enums.OutputType;
import ai.platform.aiassit.service.ai.api.enums.ProviderType;
import ai.platform.aiassit.service.ai.api.enums.ResponseFormatType;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.chat.core.workflow.node.BaseWorkflowNode;
import ai.platform.aiassit.chat.core.workflow.support.WorkflowHistoryRecorder;
import ai.platform.aiassit.chat.history.enums.AiChatActorType;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactStage;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
import ai.platform.aiassit.chat.history.enums.AiChatDisplayLevel;
import ai.platform.aiassit.chat.history.enums.AiChatMessageType;
import ai.platform.aiassit.render.api.RenderInternalApi;
import ai.platform.aiassit.render.api.dto.RenderDetailDTO;
import ai.platform.aiassit.render.api.dto.RenderUpsertRequest;
import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 渲染节点，负责构建结构化 render json，并写入最终产物。
 *
 * <p>当前节点不再生成自然语言总结，而是消费前序规划、知识检索、SQL 预生成结果，调用 AI Agent
 * 生成后续页面可消费的 render json。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Service
@Slf4j
public class RenderNode extends BaseWorkflowNode {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String DEFAULT_SCENE = "ai-chat-render-json";
    private static final String DEFAULT_RENDER_PAGE_PREFIX = "ai-chat-render";
    private static final String TOOL_RENDER_VALIDATE = "render_json_validate_tool";
    private static final String RENDER_PROMPT = """
            你是智能问数工作流的 Render JSON 构建节点。
            你的任务不是总结回答，而是基于前序节点已经准备好的查询规划、知识上下文、SQL 预生成结果和伪 SQL，
            直接构建一个可供后续渲染链路消费的 render json。

            输出要求：
            1. 最终只输出严格合法的 render json，不要输出 markdown、解释、代码块。
            2. render json 根节点必须是一个对象，优先包含 component 或 type，并可包含 children。
            3. 组件节点允许包含 props、style、events、slots、meta、children。
            4. 如果数据仍不完整，可以输出占位说明组件，但不能伪造不存在的执行结果。
            5. 应优先把“查询规划”“关键假设”“伪 SQL”“待确认事项”组织成结构化展示节点，而不是纯文本长段落。
            6. 在最终输出前，请使用 render_json_validate_tool 做结构合法性检查，并根据错误进行修正。
            """;

    private final AiChatExecutionApi aiChatExecutionApi;
    private final RenderInternalApi renderInternalApi;
    private final WorkflowHistoryRecorder historyRecorder;
    private final ObjectMapper objectMapper;

    public RenderNode(AiChatExecutionApi aiChatExecutionApi,
                      RenderInternalApi renderInternalApi,
                      WorkflowHistoryRecorder historyRecorder,
                      ObjectMapper objectMapper) {
        this.aiChatExecutionApi = aiChatExecutionApi;
        this.renderInternalApi = renderInternalApi;
        this.historyRecorder = historyRecorder;
        this.objectMapper = objectMapper;
    }

    @Override
    protected NodeResult doExecute(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        if (command == null) {
            return NodeResult.fail("command is required");
        }
        if (context.getRound() == null) {
            return NodeResult.fail("round is required");
        }

        try {
            ChatRequest request = buildRenderRequest(command, context);
            context.getOrCreateNodeResult(WorkflowNodeCodes.RENDER.getNodeCode()).setRequest(request);

            ChatResponse response = aiChatExecutionApi.chat(request).getData();
            context.getOrCreateNodeResult(WorkflowNodeCodes.RENDER.getNodeCode()).setResponse(response);

            Map<String, Object> renderJson = extractRenderJson(response);
            validateRenderJson(renderJson);

            String renderJsonText = toPrettyJson(renderJson);
            String renderCheckReport = buildRenderCheckReport(renderJson);

            context.setRenderJson(renderJson);
            if (StringUtils.hasText(renderCheckReport)) {
                context.setRenderCheckReport(renderCheckReport);
                context.put(WorkflowContextKeys.Render.RENDER_CHECK_REPORT, renderCheckReport);
            }
            RenderDetailDTO renderPage = persistRenderPage(context, renderJsonText);
            context.setRenderedAnswer(renderJsonText);
            context.getOrCreateNodeResult(WorkflowNodeCodes.RENDER.getNodeCode()).setStatus(STATUS_SUCCESS);
            context.put(WorkflowContextKeys.Render.RENDER_JSON, renderJson);
            context.put(WorkflowContextKeys.Render.RENDERED_ANSWER, renderJsonText);
            context.publishEvent("answer-ready",
                    buildAnswerReadyMessage(renderPage),
                    renderJsonText,
                    null,
                    STATUS_SUCCESS);

            persistAssistantMessage(context, renderJsonText);
            historyRecorder.saveArtifact(
                    context,
                    AiChatArtifactType.MODEL_RESPONSE_SNAPSHOT.name(),
                    AiChatArtifactStage.RENDER.name(),
                    "Render JSON",
                    renderJson,
                    AiChatContentFormat.JSON.name(),
                    true,
                    STATUS_SUCCESS,
                    context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                    response == null ? null : response.getRequestId()
            );
            return NodeResult.success(null);
        } catch (Exception ex) {
            log.error("render node failed, roundCode={}", context.getRound().getRoundCode(), ex);
            context.getOrCreateNodeResult(WorkflowNodeCodes.RENDER.getNodeCode()).setStatus(STATUS_FAILED);
            historyRecorder.saveArtifact(
                    context,
                    AiChatArtifactType.WORKFLOW_ERROR.name(),
                    AiChatArtifactStage.RENDER.name(),
                    "Render JSON 构建失败",
                    ex.getMessage(),
                    AiChatContentFormat.PLAIN_TEXT.name(),
                    true,
                    STATUS_FAILED,
                    context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                    null
            );
            return NodeResult.fail(ex.getMessage());
        }
    }

    @Override
    public String code() {
        return WorkflowNodeCodes.RENDER.getNodeCode();
    }

    @Override
    public int order() {
        return 700;
    }

    private ChatRequest buildRenderRequest(AiChatQueryCommand command, WorkflowContext context) {
        ChatRequest request = new ChatRequest();
        request.setProvider(ProviderType.AI_AGENT);
        request.setModel(resolveAgentModel(command));
        request.setMessages(List.of(
                buildMessage(MessageRole.SYSTEM, RENDER_PROMPT),
                buildMessage(MessageRole.USER, buildRenderInput(command, context))
        ));
        request.setTools(buildRenderTools());
        request.setResponseFormat(buildRenderResponseFormat());

        ChatOptions options = new ChatOptions();
        options.setMaxTokens(4096);
        options.setTimeoutMs(30_000);
        request.setOptions(options);

        RequestMeta meta = new RequestMeta();
        meta.setTraceId(command.getTraceId());
        meta.setScene(StringUtils.hasText(command.getScene()) ? command.getScene() : DEFAULT_SCENE);
        request.setMeta(meta);

        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("enabledTools", List.of(TOOL_RENDER_VALIDATE));
        ext.put("task", "build_render_json");
        ext.put("nodeCode", WorkflowNodeCodes.RENDER.getNodeCode());
        request.setExt(ext);
        return request;
    }

    private ChatMessage buildMessage(MessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private List<ToolDefinition> buildRenderTools() {
        List<ToolDefinition> tools = new ArrayList<>();
        tools.add(buildRenderTool(
                TOOL_RENDER_VALIDATE,
                "Validate render JSON syntax and base component tree structure."
        ));
        return tools;
    }

    private ToolDefinition buildRenderTool(String name, String description) {
        ToolDefinition definition = new ToolDefinition();
        definition.setName(name);
        definition.setDescription(description);
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> renderJsonProperty = new LinkedHashMap<>();
        renderJsonProperty.put("type", "string");
        renderJsonProperty.put("description", "The render JSON string to inspect.");
        properties.put("render_json", renderJsonProperty);
        schema.put("properties", properties);
        schema.put("required", List.of("render_json"));
        definition.setInputSchema(schema);
        return definition;
    }

    private ResponseFormat buildRenderResponseFormat() {
        ResponseFormat responseFormat = new ResponseFormat();
        responseFormat.setType(ResponseFormatType.JSON_SCHEMA);

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("description", "Render JSON root node.");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("component", Map.of("type", "string"));
        properties.put("type", Map.of("type", "string"));
        properties.put("name", Map.of("type", "string"));
        properties.put("props", buildFreeFormObjectSchema("Component props."));
        properties.put("style", buildFreeFormObjectSchema("Component style object."));
        properties.put("events", buildFreeFormObjectSchema("Component event bindings."));
        properties.put("slots", buildFreeFormObjectSchema("Component slots."));
        properties.put("meta", buildFreeFormObjectSchema("Render metadata."));

        Map<String, Object> childrenSchema = new LinkedHashMap<>();
        childrenSchema.put("type", "array");
        Map<String, Object> childItem = new LinkedHashMap<>();
        childItem.put("type", "object");
        childItem.put("additionalProperties", true);
        childrenSchema.put("items", childItem);
        properties.put("children", childrenSchema);

        schema.put("properties", properties);
        schema.put("additionalProperties", true);
        responseFormat.setSchema(schema);
        return responseFormat;
    }

    private Map<String, Object> buildFreeFormObjectSchema(String description) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("description", description);
        schema.put("additionalProperties", true);
        return schema;
    }

    private String buildRenderInput(AiChatQueryCommand command, WorkflowContext context) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userQuery", command.getMessage());
        payload.put("planningSummary", context.getAnalysisResult());
        payload.put("planningResult", context.get(WorkflowContextKeys.Planning.QUERY_PLAN_RESULT));
        payload.put("promptContext", context.getPromptContext(WorkflowNodeCodes.RENDER.getNodeCode()));
        payload.put("knowledgeContext", context.getKnowledgeResult());
        payload.put("knowledgeSearchResponse", context.get(WorkflowContextKeys.Capability.KNOWLEDGE_SEARCH_RESPONSE));
        payload.put("sqlPreGenerateResult", context.getSqlPreGenerateResult());
        payload.put("pseudoSql", context.getGeneratedSql());
        payload.put("renderRequirements", List.of(
                "优先使用卡片、描述块、列表、表格、代码块等可解释节点表达结果",
                "需要显式展示关键假设、未确认项和下一步建议",
                "如果只有伪 SQL，不要伪造成真实查询结果"
        ));
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize render input", ex);
        }
    }

    private Map<String, Object> extractRenderJson(ChatResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getOutputs())) {
            throw new IllegalArgumentException("render response is empty");
        }
        for (OutputItem item : response.getOutputs()) {
            if (item == null) {
                continue;
            }
            if (item.getType() == OutputType.JSON) {
                if (StringUtils.hasText(item.getText())) {
                    try {
                        return objectMapper.readValue(item.getText(), new TypeReference<LinkedHashMap<String, Object>>() {
                        });
                    } catch (Exception ignored) {
                        // Fall back to the json field below.
                    }
                }
                if (item.getJson() != null && !item.getJson().isEmpty()) {
                    return objectMapper.convertValue(item.getJson(), new TypeReference<LinkedHashMap<String, Object>>() {
                    });
                }
            }
        }
        for (OutputItem item : response.getOutputs()) {
            if (item == null || !StringUtils.hasText(item.getText())) {
                continue;
            }
            try {
                return objectMapper.readValue(item.getText(), new TypeReference<LinkedHashMap<String, Object>>() {
                });
            } catch (Exception ignored) {
                // Try the next output item.
            }
        }
        throw new IllegalArgumentException("render json output is missing");
    }

    private void validateRenderJson(Map<String, Object> renderJson) {
        if (renderJson == null || renderJson.isEmpty()) {
            throw new IllegalArgumentException("render json is empty");
        }
        if (!hasNodeIdentity(renderJson) && !(renderJson.get("children") instanceof List<?>)) {
            throw new IllegalArgumentException("render json root must define component/type/name or children");
        }
        validateRenderNode(renderJson, "$");
    }

    private void validateRenderNode(Object node, String path) {
        if (!(node instanceof Map<?, ?> nodeMap)) {
            throw new IllegalArgumentException("render node at " + path + " must be an object");
        }
        Object children = nodeMap.get("children");
        if (children == null) {
            return;
        }
        if (!(children instanceof List<?> childList)) {
            throw new IllegalArgumentException(path + ".children must be a list");
        }
        for (int i = 0; i < childList.size(); i++) {
            Object child = childList.get(i);
            if (!(child instanceof Map<?, ?> childMap)) {
                throw new IllegalArgumentException(path + ".children[" + i + "] must be an object");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> childNode = (Map<String, Object>) childMap;
            if (!hasNodeIdentity(childNode) && !(childNode.get("children") instanceof List<?>)) {
                throw new IllegalArgumentException(path + ".children[" + i + "] must define component/type/name or children");
            }
            validateRenderNode(childNode, path + ".children[" + i + "]");
        }
    }

    private boolean hasNodeIdentity(Map<String, Object> node) {
        return hasText(node.get("component"))
                || hasText(node.get("type"))
                || hasText(node.get("name"));
    }

    private boolean hasText(Object value) {
        return value instanceof String str && StringUtils.hasText(str);
    }

    private String buildRenderCheckReport(Map<String, Object> renderJson) {
        int componentCount = countComponents(renderJson);
        String root = resolveNodeLabel(renderJson);
        String report = "Render JSON 校验通过，root=" + root + "，componentCount=" + componentCount + "。";
        return limitLength(report, 200);
    }

    private int countComponents(Object node) {
        if (!(node instanceof Map<?, ?> nodeMap)) {
            return 0;
        }
        int count = hasNodeIdentity(castNode(nodeMap)) ? 1 : 0;
        Object children = nodeMap.get("children");
        if (children instanceof List<?> childList) {
            for (Object child : childList) {
                count += countComponents(child);
            }
        }
        return count;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castNode(Map<?, ?> node) {
        return (Map<String, Object>) node;
    }

    private String resolveNodeLabel(Map<String, Object> node) {
        Object component = node.get("component");
        if (hasText(component)) {
            return String.valueOf(component);
        }
        Object type = node.get("type");
        if (hasText(type)) {
            return String.valueOf(type);
        }
        Object name = node.get("name");
        if (hasText(name)) {
            return String.valueOf(name);
        }
        return "anonymous-root";
    }

    private RenderDetailDTO persistRenderPage(WorkflowContext context, String renderJsonText) {
        RenderUpsertRequest request = new RenderUpsertRequest();
        request.setCode(resolveRenderPageCode(context));
        request.setName(resolveRenderPageName(context));
        request.setCategoryCode(resolveRenderCategoryCode(context));
        request.setStatus(EffectiveStatus.PUBLISHED);
        request.setContent(renderJsonText);

        RenderDetailDTO detail = renderInternalApi.upsert(request);
        String pageCode = detail == null ? request.getCode() : detail.getCode();
        context.put(WorkflowContextKeys.Render.RENDER_PAGE_CODE, pageCode);
        context.put(WorkflowContextKeys.Render.RENDER_PAGE_DETAIL, detail);
        context.putNodeMetadata(WorkflowNodeCodes.RENDER.getNodeCode(), WorkflowContextKeys.Render.RENDER_PAGE_CODE, pageCode);
        log.info("render page persisted, roundCode={}, pageCode={}", context.getRound().getRoundCode(), pageCode);
        return detail;
    }

    private String buildAnswerReadyMessage(RenderDetailDTO renderPage) {
        if (renderPage == null || !StringUtils.hasText(renderPage.getCode())) {
            return "render json generated and persisted";
        }
        return "render json generated and persisted, pageCode=" + renderPage.getCode();
    }

    private String resolveRenderPageCode(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        String explicit = firstText(
                readExt(command, "renderPageCode"),
                readExt(command, "pageCode"),
                readExt(command, "render_code")
        );
        if (StringUtils.hasText(explicit)) {
            return sanitizePageCode(explicit);
        }
        List<String> segments = new ArrayList<>();
        segments.add(DEFAULT_RENDER_PAGE_PREFIX);
        if (context.getSession() != null && StringUtils.hasText(context.getSession().getSessionCode())) {
            segments.add(context.getSession().getSessionCode().trim());
        }
        if (context.getRound() != null && StringUtils.hasText(context.getRound().getRoundCode())) {
            segments.add(context.getRound().getRoundCode().trim());
        }
        return sanitizePageCode(String.join("-", segments));
    }

    private String resolveRenderPageName(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        Object planningResult = context.get(WorkflowContextKeys.Planning.QUERY_PLAN_RESULT);
        String explicit = firstText(
                readExt(command, "renderPageName"),
                readExt(command, "pageName"),
                nestedText(planningResult, "ext", "renderPageName"),
                nestedText(planningResult, "ext", "pageName"),
                nestedText(planningResult, "title"),
                command == null ? null : command.getSessionName(),
                command == null ? null : command.getMessage()
        );
        return limitLength(explicit, 80);
    }

    private String resolveRenderCategoryCode(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        Object planningResult = context.get(WorkflowContextKeys.Planning.QUERY_PLAN_RESULT);
        return firstText(
                readExt(command, "renderCategoryCode"),
                readExt(command, "categoryCode"),
                nestedText(planningResult, "ext", "renderCategoryCode"),
                nestedText(planningResult, "ext", "categoryCode")
        );
    }

    private Object readExt(AiChatQueryCommand command, String key) {
        return command == null || command.getExt() == null ? null : command.getExt().get(key);
    }

    @SuppressWarnings("unchecked")
    private String nestedText(Object root, String... path) {
        Object current = root;
        for (String key : path) {
            if (!(current instanceof Map<?, ?>)) {
                if (current == null) {
                    return null;
                }
                current = objectMapper.convertValue(current, new TypeReference<Map<String, Object>>() {
                });
            }
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = ((Map<String, Object>) map).get(key);
        }
        return current instanceof String str && StringUtils.hasText(str) ? str.trim() : null;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value instanceof String str && StringUtils.hasText(str)) {
                return str.trim();
            }
        }
        return null;
    }

    private String sanitizePageCode(String value) {
        if (!StringUtils.hasText(value)) {
            return DEFAULT_RENDER_PAGE_PREFIX;
        }
        String normalized = value.trim().toLowerCase()
                .replaceAll("[^a-z0-9-_]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isEmpty()) {
            return DEFAULT_RENDER_PAGE_PREFIX;
        }
        Set<String> segments = new LinkedHashSet<>(List.of(normalized.split("-")));
        String collapsed = String.join("-", segments);
        return collapsed.length() > 128 ? collapsed.substring(0, 128) : collapsed;
    }

    private void persistAssistantMessage(WorkflowContext context, String answer) {
        historyRecorder.saveMessage(
                context,
                context.getRound().getRoundCode(),
                "ASSISTANT",
                AiChatActorType.AI.name(),
                AiChatMessageType.FINAL_ANSWER.name(),
                answer,
                AiChatContentFormat.JSON.name(),
                AiChatDisplayLevel.VISIBLE.name(),
                STATUS_SUCCESS,
                context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                null
        );
    }

    private String toPrettyJson(Map<String, Object> renderJson) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(renderJson);
        } catch (Exception ex) {
            throw new IllegalStateException("failed to serialize render json", ex);
        }
    }

    private String limitLength(String text, int maxLength) {
        if (!StringUtils.hasText(text) || text.length() <= maxLength) {
            return text;
        }
        if (maxLength <= 1) {
            return text.substring(0, maxLength);
        }
        return text.substring(0, maxLength - 1) + "…";
    }

    private String resolveAgentModel(AiChatQueryCommand command) {
        Object explicit = command == null || command.getExt() == null ? null : command.getExt().get("renderAgentModel");
        if (!(explicit instanceof String) || !StringUtils.hasText((String) explicit)) {
            explicit = command == null || command.getExt() == null ? null : command.getExt().get("aiAgentModel");
        }
        if (explicit instanceof String model && StringUtils.hasText(model)) {
            return model.trim();
        }
        if (command != null && StringUtils.hasText(command.getApiModel())) {
            String apiModel = command.getApiModel().trim();
            if (apiModel.startsWith("gpt-")) {
                return apiModel;
            }
        }
        return "gpt-5.5";
    }
}

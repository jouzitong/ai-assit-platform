package ai.platform.aiassit.chat.core.workflow.node.impl;

import ai.platform.aiassist.service.ai.api.AiChatExecutionApi;
import ai.platform.aiassist.service.ai.api.AiMetaQueryApi;
import ai.platform.aiassist.service.ai.api.dto.AiMetaQueryRequest;
import ai.platform.aiassist.service.ai.api.dto.AiModelConfigDTO;
import ai.platform.aiassist.service.ai.api.dto.ChatMessage;
import ai.platform.aiassist.service.ai.api.dto.ChatOptions;
import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.dto.OutputItem;
import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import ai.platform.aiassist.service.ai.api.enums.MessageRole;
import ai.platform.aiassist.service.ai.api.enums.ProviderType;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.workflow.bean.NodeResult;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.chat.core.workflow.node.BaseWorkflowNode;
import ai.platform.aiassit.chat.core.workflow.support.WorkflowHistoryRecorder;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactStage;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * SQL 生成节点，负责基于规划和知识上下文生成候选 SQL。
 *
 * <p>功能：</p>
 * <ul>
 *     <li>消费用户问题、查询规划、知识上下文和必要历史消息。</li>
 *     <li>调用模型生成候选 SQL 草案。</li>
 *     <li>对模型输出做最小清洗，并将候选 SQL 写入 {@link WorkflowContext}。</li>
 *     <li>记录 SQL 草案 artifact，供后续校验节点继续处理。</li>
 * </ul>
 *
 * <p>边界描述：</p>
 * <ul>
 *     <li>只负责生成候选 SQL，不判定最终安全性与可执行性。</li>
 *     <li>不负责知识检索，不负责真实执行，不负责最终答案渲染。</li>
 *     <li>即使携带假设说明，也只输出 SQL 文本，不扩展成解释性回答。</li>
 * </ul>
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Service
@Slf4j
public class SqlGenerateNode extends BaseWorkflowNode {

    private static final String DEFAULT_SCENE = "ai-chat-sql-generate";
    private static final String SQL_GENERATION_POLICY_KEY = "sqlGenerationPolicy";
    private static final String USER_PREFERENCE_KEY = "resolvedUserPreferences";
    private static final String SQL_GENERATION_PROMPT = """
            你是一个 NL2SQL 生成节点。
            请严格根据提供的用户问题、查询规划和知识上下文，生成一条候选 SQL。
            你还会收到“SQL 生成规范”和“用户偏好”两个补充部分：
            - SQL 生成规范属于硬约束，必须遵守
            - 用户偏好属于软参考，仅在不与用户本轮要求和硬约束冲突时采用

            约束要求：
            1. 优先输出单条 SELECT 或 WITH 查询
            2. 不允许输出 INSERT、UPDATE、DELETE、DROP、ALTER、TRUNCATE、CREATE、MERGE
            3. 如果上下文不足，也要尽量输出最合理的查询，并在 SQL 前用单行注释说明假设
            4. 最终输出只包含 SQL 文本，可带 SQL 注释，不要解释
            """;

    private final AiChatExecutionApi aiChatExecutionApi;
    private final AiMetaQueryApi aiMetaQueryApi;
    private final WorkflowHistoryRecorder historyRecorder;

    public SqlGenerateNode(AiChatExecutionApi aiChatExecutionApi,
                           AiMetaQueryApi aiMetaQueryApi,
                           WorkflowHistoryRecorder historyRecorder) {
        this.aiChatExecutionApi = aiChatExecutionApi;
        this.aiMetaQueryApi = aiMetaQueryApi;
        this.historyRecorder = historyRecorder;
    }

    @Override
    protected NodeResult doExecute(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        if (command == null) {
            return NodeResult.fail("command is required");
        }
        if (!StringUtils.hasText(context.getAnalysisResult())) {
            return NodeResult.fail("analysisResult is required");
        }

        try {
            ChatRequest request = buildRequest(command, context);
            context.getOrCreateNodeResult(WorkflowNodeCodes.SQL_GENERATE.getNodeCode()).setRequest(request);
            context.getOrCreateNodeResult(WorkflowNodeCodes.SQL_GENERATE.getNodeCode()).setStatus("RUNNING");
            ChatResponse response = aiChatExecutionApi.chat(request).getData();
            context.getOrCreateNodeResult(WorkflowNodeCodes.SQL_GENERATE.getNodeCode()).setResponse(response);
            String generatedSql = normalizeSql(extractAnswer(response));
            if (!StringUtils.hasText(generatedSql)) {
                return NodeResult.fail("generated sql is empty");
            }
            context.setGeneratedSql(generatedSql);
            context.putNodeOutput(WorkflowNodeCodes.SQL_GENERATE.getNodeCode(), "requestId", response == null ? null : response.getRequestId());
            context.put("generatedSql", generatedSql);
            context.put("sqlGenerateRequestId", response == null ? null : response.getRequestId());
            context.getOrCreateNodeResult(WorkflowNodeCodes.SQL_GENERATE.getNodeCode()).setStatus("SUCCESS");
            historyRecorder.saveArtifact(
                    context,
                    AiChatArtifactType.SQL_DRAFT.name(),
                    AiChatArtifactStage.SQL_GEN.name(),
                    "SQL 草案",
                    generatedSql,
                    AiChatContentFormat.SQL.name(),
                    true,
                    "SUCCESS",
                    context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                    response == null ? null : response.getRequestId()
            );
            return NodeResult.success(null);
        } catch (Exception ex) {
            log.error("sql generate failed, sessionCode={}", context.getSession() == null ? null : context.getSession().getSessionCode(), ex);
            context.getOrCreateNodeResult(WorkflowNodeCodes.SQL_GENERATE.getNodeCode()).setStatus("FAILED");
            historyRecorder.saveArtifact(
                    context,
                    AiChatArtifactType.WORKFLOW_ERROR.name(),
                    AiChatArtifactStage.SQL_GEN.name(),
                    "SQL 生成失败",
                    ex.getMessage(),
                    AiChatContentFormat.PLAIN_TEXT.name(),
                    true,
                    "FAILED",
                    context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                    null
            );
            return NodeResult.fail(ex.getMessage());
        }
    }

    @Override
    public String code() {
        return WorkflowNodeCodes.SQL_GENERATE.getNodeCode();
    }

    @Override
    public int order() {
        return 400;
    }

    private ChatRequest buildRequest(AiChatQueryCommand command, WorkflowContext context) {
        ChatRequest request = new ChatRequest();
        request.setProvider(resolveProviderType(command.getApiModel()));
        request.setModel(resolveActualModel(command.getApiModel()));

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(buildMessage(MessageRole.SYSTEM, SQL_GENERATION_PROMPT));
        messages.add(buildMessage(MessageRole.USER, buildSqlGenerationInput(command, context)));
        request.setMessages(messages);

        ChatOptions options = new ChatOptions();
        options.setMaxTokens(resolveMaxTokens(command.getApiModel()));
        options.setTimeoutMs(30_000);
        request.setOptions(options);

        RequestMeta meta = new RequestMeta();
        meta.setTraceId(command.getTraceId());
        meta.setScene(StringUtils.hasText(command.getScene()) ? command.getScene() : DEFAULT_SCENE);
        request.setMeta(meta);
        return request;
    }

    private ChatMessage buildMessage(MessageRole role, String content) {
        ChatMessage message = new ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private String buildSqlGenerationInput(AiChatQueryCommand command, WorkflowContext context) {
        StringBuilder builder = new StringBuilder();
        List<AiChatMessageDTO> sessionMessages = context.getOrCreateUserMessageContext().getSessionMessages();
        builder.append("用户问题：\n").append(command.getMessage()).append("\n\n");
        builder.append("查询规划：\n").append(context.getAnalysisResult()).append("\n\n");
        appendStructuredSection(builder, "SQL 生成规范", context.get(SQL_GENERATION_POLICY_KEY));
        appendStructuredSection(builder, "用户偏好", context.get(USER_PREFERENCE_KEY));
        if (StringUtils.hasText(context.getPromptContext(WorkflowNodeCodes.SQL_GENERATE.getNodeCode()))) {
            builder.append("Prompt 上下文：\n")
                    .append(context.getPromptContext(WorkflowNodeCodes.SQL_GENERATE.getNodeCode()))
                    .append("\n\n");
        }
        if (StringUtils.hasText(context.getKnowledgeResult())) {
            builder.append("知识上下文：\n").append(context.getKnowledgeResult()).append("\n\n");
        }
        if (!CollectionUtils.isEmpty(sessionMessages)) {
            builder.append("历史消息：\n");
            for (int i = 0; i < sessionMessages.size(); i++) {
                builder.append(i + 1)
                        .append(". ")
                        .append(sessionMessages.get(i).getRole())
                        .append(": ")
                        .append(sessionMessages.get(i).getContent())
                        .append('\n');
            }
        }
        return builder.toString();
    }

    private void appendStructuredSection(StringBuilder builder, String title, Object value) {
        String rendered = renderValue(value, 0);
        if (!StringUtils.hasText(rendered)) {
            return;
        }
        builder.append(title).append("：\n").append(rendered).append("\n\n");
    }

    private String renderValue(Object value, int indent) {
        if (value == null) {
            return "";
        }
        if (value instanceof String str) {
            return str.trim();
        }
        String indentText = "  ".repeat(Math.max(0, indent));
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String renderedChild = renderValue(entry.getValue(), indent + 1);
                if (!StringUtils.hasText(renderedChild)) {
                    continue;
                }
                builder.append(indentText)
                        .append("- ")
                        .append(entry.getKey())
                        .append(": ");
                if (entry.getValue() instanceof Map<?, ?> || entry.getValue() instanceof List<?>) {
                    builder.append('\n').append(renderedChild).append('\n');
                } else {
                    builder.append(renderedChild).append('\n');
                }
            }
            return builder.toString().trim();
        }
        if (value instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            for (Object item : list) {
                String renderedChild = renderValue(item, indent + 1);
                if (!StringUtils.hasText(renderedChild)) {
                    continue;
                }
                builder.append(indentText).append("- ").append(renderedChild).append('\n');
            }
            return builder.toString().trim();
        }
        return String.valueOf(value);
    }

    private String extractAnswer(ChatResponse response) {
        if (response == null || CollectionUtils.isEmpty(response.getOutputs())) {
            return "";
        }
        return response.getOutputs().stream()
                .filter(Objects::nonNull)
                .map(OutputItem::getText)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("");
    }

    private String normalizeSql(String sql) {
        if (!StringUtils.hasText(sql)) {
            return sql;
        }
        String normalized = sql.trim();
        normalized = normalized.replace("```sql", "");
        normalized = normalized.replace("```SQL", "");
        normalized = normalized.replace("```", "");
        return normalized.trim();
    }

    private ProviderType resolveProviderType(String apiModel) {
        AiModelConfigDTO config = findModelConfigByApiModel(apiModel);
        if (config != null && StringUtils.hasText(config.getProviderCode())) {
            try {
                return ProviderType.valueOf(config.getProviderCode().trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                return ProviderType.DASHSCOPE;
            }
        }
        return ProviderType.DASHSCOPE;
    }

    private AiModelConfigDTO findModelConfigByApiModel(String apiModel) {
        AiMetaQueryRequest request = new AiMetaQueryRequest();
        request.setEnabled(Boolean.TRUE);
        return aiMetaQueryApi.listModels(request).stream()
                .filter(Objects::nonNull)
                .filter(config -> StringUtils.hasText(config.getApiModel()))
                .filter(config -> !StringUtils.hasText(apiModel) || apiModel.trim().equals(config.getApiModel().trim()))
                .findFirst()
                .orElse(null);
    }

    private int resolveMaxTokens(String apiModel) {
        AiModelConfigDTO config = findModelConfigByApiModel(apiModel);
        return config == null || config.getMaxOutputTokens() == null ? 1024 : config.getMaxOutputTokens();
    }

    private String resolveActualModel(String apiModel) {
        if (StringUtils.hasText(apiModel)) {
            return apiModel.trim();
        }
        AiModelConfigDTO config = findModelConfigByApiModel(null);
        if (config != null && StringUtils.hasText(config.getApiModel())) {
            return config.getApiModel().trim();
        }
        if (config != null && StringUtils.hasText(config.getModelCode())) {
            return config.getModelCode().trim();
        }
        return "qwen-math-turbo";
    }
}

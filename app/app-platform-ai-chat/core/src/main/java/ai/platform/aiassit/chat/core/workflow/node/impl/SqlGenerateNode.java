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
import ai.platform.aiassit.chat.core.workflow.node.BaseWorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * SQL 生成节点，负责基于规划和知识上下文生成候选 SQL。
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Service
@Slf4j
public class SqlGenerateNode extends BaseWorkflowNode {

    private static final String DEFAULT_SCENE = "ai-chat-sql-generate";
    private static final String SQL_GENERATION_PROMPT = """
            你是一个 NL2SQL 生成节点。
            请严格根据提供的用户问题、查询规划和知识上下文，生成一条候选 SQL。

            约束要求：
            1. 优先输出单条 SELECT 或 WITH 查询
            2. 不允许输出 INSERT、UPDATE、DELETE、DROP、ALTER、TRUNCATE、CREATE、MERGE
            3. 如果上下文不足，也要尽量输出最合理的查询，并在 SQL 前用单行注释说明假设
            4. 最终输出只包含 SQL 文本，可带 SQL 注释，不要解释
            """;

    private final AiChatExecutionApi aiChatExecutionApi;
    private final AiMetaQueryApi aiMetaQueryApi;

    public SqlGenerateNode(AiChatExecutionApi aiChatExecutionApi,
                           AiMetaQueryApi aiMetaQueryApi) {
        this.aiChatExecutionApi = aiChatExecutionApi;
        this.aiMetaQueryApi = aiMetaQueryApi;
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
            ChatResponse response = aiChatExecutionApi.chat(request);
            String generatedSql = normalizeSql(extractAnswer(response));
            if (!StringUtils.hasText(generatedSql)) {
                return NodeResult.fail("generated sql is empty");
            }
            context.setGeneratedSql(generatedSql);
            context.put("generatedSql", generatedSql);
            context.put("sqlGenerateRequestId", response == null ? null : response.getRequestId());
            return NodeResult.success(null);
        } catch (Exception ex) {
            log.error("sql generate failed, sessionCode={}", context.getSession() == null ? null : context.getSession().getSessionCode(), ex);
            return NodeResult.fail(ex.getMessage());
        }
    }

    @Override
    public String type() {
        return "Sql-Generate";
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
        builder.append("用户问题：\n").append(command.getMessage()).append("\n\n");
        builder.append("查询规划：\n").append(context.getAnalysisResult()).append("\n\n");
        if (StringUtils.hasText(context.getKnowledgeResult())) {
            builder.append("知识上下文：\n").append(context.getKnowledgeResult()).append("\n\n");
        }
        if (!CollectionUtils.isEmpty(context.getSessionMessages())) {
            builder.append("历史消息：\n");
            for (int i = 0; i < context.getSessionMessages().size(); i++) {
                builder.append(i + 1)
                        .append(". ")
                        .append(context.getSessionMessages().get(i).getRole())
                        .append(": ")
                        .append(context.getSessionMessages().get(i).getContent())
                        .append('\n');
            }
        }
        return builder.toString();
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

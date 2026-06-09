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
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 渲染节点，负责组织最终回复、落库助手消息并结束轮次。
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Service
@Slf4j
public class RenderNode extends BaseWorkflowNode {

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String DEFAULT_SCENE = "ai-chat-render";
    private static final String RENDER_PROMPT = """
            你是智能问数工作流的最终渲染节点。
            请基于用户问题、查询规划、知识上下文、SQL 与执行结果，生成一段最终回复。

            要求：
            1. 用中文直接回答
            2. 如果 SQL 实际未执行，需要明确说明当前只生成了 SQL 草案
            3. 回答里要尽量包含关键结论、主要假设和下一步建议
            4. 不要输出 JSON
            """;

    private final AiChatExecutionApi aiChatExecutionApi;
    private final AiMetaQueryApi aiMetaQueryApi;
    private final AiChatMessageService messageService;
    private final AiChatRoundService roundService;

    public RenderNode(AiChatExecutionApi aiChatExecutionApi,
                      AiMetaQueryApi aiMetaQueryApi,
                      AiChatMessageService messageService,
                      AiChatRoundService roundService) {
        this.aiChatExecutionApi = aiChatExecutionApi;
        this.aiMetaQueryApi = aiMetaQueryApi;
        this.messageService = messageService;
        this.roundService = roundService;
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
            String answer = buildRenderedAnswer(command, context);
            if (!StringUtils.hasText(answer)) {
                answer = buildFallbackAnswer(context);
            }

            context.setRenderedAnswer(answer);
            context.put("renderedAnswer", answer);
            persistAssistantMessage(context, answer);
            finishRound(context.getRound(), STATUS_SUCCESS, resolveActualModel(command.getApiModel()));
            return NodeResult.success(null);
        } catch (Exception ex) {
            log.error("render node failed, roundCode={}", context.getRound().getRoundCode(), ex);
            finishRound(context.getRound(), STATUS_FAILED, resolveActualModel(command.getApiModel()));
            return NodeResult.fail(ex.getMessage());
        }
    }

    @Override
    public String type() {
        return "Render";
    }

    @Override
    public int order() {
        return 700;
    }

    private String buildRenderedAnswer(AiChatQueryCommand command, WorkflowContext context) {
        ChatRequest request = new ChatRequest();
        request.setProvider(resolveProviderType(command.getApiModel()));
        request.setModel(resolveActualModel(command.getApiModel()));

        ChatMessage systemMessage = new ChatMessage();
        systemMessage.setRole(MessageRole.SYSTEM);
        systemMessage.setContent(RENDER_PROMPT);

        ChatMessage userMessage = new ChatMessage();
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent(buildRenderInput(command, context));

        request.setMessages(java.util.List.of(systemMessage, userMessage));

        ChatOptions options = new ChatOptions();
        options.setMaxTokens(resolveMaxTokens(command.getApiModel()));
        options.setTimeoutMs(30_000);
        request.setOptions(options);

        RequestMeta meta = new RequestMeta();
        meta.setTraceId(command.getTraceId());
        meta.setScene(StringUtils.hasText(command.getScene()) ? command.getScene() : DEFAULT_SCENE);
        request.setMeta(meta);

        ChatResponse response = aiChatExecutionApi.chat(request);
        context.setEngineResponse(response);
        return extractAnswer(response);
    }

    private String buildRenderInput(AiChatQueryCommand command, WorkflowContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户问题：\n").append(command.getMessage()).append("\n\n");
        builder.append("查询规划：\n").append(defaultIfBlank(context.getAnalysisResult(), "无")).append("\n\n");
        builder.append("知识上下文：\n").append(defaultIfBlank(context.getKnowledgeResult(), "无")).append("\n\n");
        builder.append("候选 SQL：\n").append(defaultIfBlank(context.getValidatedSql(), context.getGeneratedSql())).append("\n\n");
        builder.append("SQL 执行状态：\n").append(defaultIfBlank(context.getSqlExecutionStatus(), "UNKNOWN")).append("\n\n");
        builder.append("SQL 执行结果：\n").append(context.getSqlExecutionResult()).append("\n");
        return builder.toString();
    }

    private String buildFallbackAnswer(WorkflowContext context) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(context.getAnalysisResult())) {
            builder.append("查询规划：").append(context.getAnalysisResult()).append("\n\n");
        }
        if (StringUtils.hasText(context.getValidatedSql())) {
            builder.append("候选 SQL：\n").append(context.getValidatedSql()).append("\n\n");
        }
        if ("SKIPPED".equalsIgnoreCase(context.getSqlExecutionStatus())) {
            builder.append("当前仅完成 SQL 草案生成，尚未接入真实执行器，请在接入 db-engine 执行能力后继续校验结果。");
        } else {
            builder.append("执行结果：").append(context.getSqlExecutionResult());
        }
        return builder.toString();
    }

    private void persistAssistantMessage(WorkflowContext context, String answer) {
        int sortNo = CollectionUtils.isEmpty(context.getSessionMessages()) ? 1 : context.getSessionMessages().size() + 1;
        AiChatMessageDTO message = new AiChatMessageDTO();
        message.setMessageCode(generateCode("msg"));
        message.setRoundCode(context.getRound().getRoundCode());
        message.setSessionCode(context.getSession().getSessionCode());
        message.setRole("ASSISTANT");
        message.setContent(answer);
        message.setSortNo(sortNo);
        messageService.add(message);
    }

    private void finishRound(AiChatRoundDTO round, String status, String actualModel) {
        if (round == null || round.getId() == null) {
            return;
        }
        AiChatRoundDTO update = new AiChatRoundDTO();
        update.setStatus(status);
        update.setActualModel(actualModel);
        update.setModelCode(actualModel);
        roundService.edit(round.getId(), update);
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

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String generateCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}

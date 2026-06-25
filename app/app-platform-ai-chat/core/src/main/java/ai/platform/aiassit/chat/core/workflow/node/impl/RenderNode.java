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
import ai.platform.aiassit.chat.core.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.chat.core.workflow.node.BaseWorkflowNode;
import ai.platform.aiassit.chat.core.workflow.support.WorkflowHistoryRecorder;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.enums.AiChatActorType;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactStage;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
import ai.platform.aiassit.chat.history.enums.AiChatDisplayLevel;
import ai.platform.aiassit.chat.history.enums.AiChatMessageType;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 渲染节点，负责组织最终回复、落库助手消息并结束轮次。
 *
 * <p>功能：</p>
 * <ul>
 *     <li>汇总用户问题、查询规划、知识上下文、预生成结果、伪 SQL、执行状态和执行结果。</li>
 *     <li>调用模型生成最终面向用户的回答，必要时回退到本地兜底文案。</li>
 *     <li>落库 assistant message 和最终回答快照 artifact。</li>
 *     <li>结束当前 round，并写入最终状态与实际使用模型。</li>
 * </ul>
 *
 * <p>边界描述：</p>
 * <ul>
 *     <li>只负责最终表达与收尾，不反向修改前序节点核心产物。</li>
 *     <li>不承担会话初始化、查询规划、知识检索、SQL 生成或执行职责。</li>
 *     <li>若前序结果不足，只能基于现有上下文兜底表达，不能伪造未发生的执行事实。</li>
 * </ul>
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
            请基于用户问题、查询规划、知识上下文、预生成结果与伪 SQL，生成一段最终回复。

            要求：
            1. 用中文直接回答
            2. 如果当前只有预生成结果或伪 SQL，需要明确说明尚未生成真实可执行 SQL
            3. 回答里要尽量包含关键结论、主要假设和下一步建议
            4. 不要输出 JSON
            """;

    private final AiChatExecutionApi aiChatExecutionApi;
    private final AiMetaQueryApi aiMetaQueryApi;
    private final AiChatRoundService roundService;
    private final WorkflowHistoryRecorder historyRecorder;

    @Autowired
    public RenderNode(AiChatExecutionApi aiChatExecutionApi,
                      AiMetaQueryApi aiMetaQueryApi,
                      AiChatRoundService roundService,
                      WorkflowHistoryRecorder historyRecorder) {
        this.aiChatExecutionApi = aiChatExecutionApi;
        this.aiMetaQueryApi = aiMetaQueryApi;
        this.roundService = roundService;
        this.historyRecorder = historyRecorder;
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
            context.getOrCreateNodeResult(WorkflowNodeCodes.RENDER.getNodeCode()).setStatus(STATUS_SUCCESS);
            context.put(WorkflowContextKeys.Render.RENDERED_ANSWER, answer);
            context.publishEvent("answer-ready", "final answer rendered", answer, null, STATUS_SUCCESS);
            persistAssistantMessage(context, answer);
            historyRecorder.saveArtifact(
                    context,
                    AiChatArtifactType.MODEL_RESPONSE_SNAPSHOT.name(),
                    AiChatArtifactStage.RENDER.name(),
                    "最终回答快照",
                    answer,
                    AiChatContentFormat.MARKDOWN.name(),
                    true,
                    STATUS_SUCCESS,
                    context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                    null
            );
            finishRound(context.getRound(), STATUS_SUCCESS, resolveActualModel(command.getApiModel()));
            return NodeResult.success(null);
        } catch (Exception ex) {
            log.error("render node failed, roundCode={}", context.getRound().getRoundCode(), ex);
            context.getOrCreateNodeResult(WorkflowNodeCodes.RENDER.getNodeCode()).setStatus(STATUS_FAILED);
            historyRecorder.saveArtifact(
                    context,
                    AiChatArtifactType.WORKFLOW_ERROR.name(),
                    AiChatArtifactStage.RENDER.name(),
                    "最终渲染失败",
                    ex.getMessage(),
                    AiChatContentFormat.PLAIN_TEXT.name(),
                    true,
                    STATUS_FAILED,
                    context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                    null
            );
            finishRound(context.getRound(), STATUS_FAILED, resolveActualModel(command.getApiModel()));
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
        context.getOrCreateNodeResult(WorkflowNodeCodes.RENDER.getNodeCode()).setRequest(request);

        ChatResponse response = aiChatExecutionApi.chat(request).getData();
        context.getOrCreateNodeResult(WorkflowNodeCodes.RENDER.getNodeCode()).setResponse(response);
        return extractAnswer(response);
    }

    private String buildRenderInput(AiChatQueryCommand command, WorkflowContext context) {
        StringBuilder builder = new StringBuilder();
        builder.append("用户问题：\n").append(command.getMessage()).append("\n\n");
        builder.append("查询规划：\n").append(defaultIfBlank(context.getAnalysisResult(), "无")).append("\n\n");
        builder.append("Prompt 上下文：\n")
                .append(defaultIfBlank(context.getPromptContext(WorkflowNodeCodes.RENDER.getNodeCode()), "无"))
                .append("\n\n");
        builder.append("知识上下文：\n").append(defaultIfBlank(context.getKnowledgeResult(), "无")).append("\n\n");
        builder.append("预生成结果：\n").append(String.valueOf(context.getSqlPreGenerateResult())).append("\n\n");
        builder.append("伪 SQL：\n").append(defaultIfBlank(context.getValidatedSql(), context.getGeneratedSql())).append("\n\n");
        builder.append("SQL 执行状态：\n").append(defaultIfBlank(context.getSqlExecutionStatus(), "UNKNOWN")).append("\n\n");
        builder.append("SQL 执行结果：\n").append(context.getSqlExecutionResult()).append("\n");
        return builder.toString();
    }

    private String buildFallbackAnswer(WorkflowContext context) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(context.getAnalysisResult())) {
            builder.append("查询规划：").append(context.getAnalysisResult()).append("\n\n");
        }
        if (context.getSqlPreGenerateResult() != null) {
            builder.append("预生成结果：").append(String.valueOf(context.getSqlPreGenerateResult())).append("\n\n");
        }
        if (StringUtils.hasText(context.getValidatedSql())) {
            builder.append("伪 SQL：\n").append(context.getValidatedSql()).append("\n\n");
        } else if (StringUtils.hasText(context.getGeneratedSql())) {
            builder.append("伪 SQL：\n").append(context.getGeneratedSql()).append("\n\n");
        }
        if ("SKIPPED".equalsIgnoreCase(context.getSqlExecutionStatus())) {
            builder.append("当前仅完成 SQL 预生成与伪 SQL 输出，尚未生成真实可执行 SQL。");
        } else if (!StringUtils.hasText(context.getSqlExecutionStatus())) {
            builder.append("当前仅完成 SQL 预生成与伪 SQL 输出，尚未进入真实 SQL 校验与执行阶段。");
        } else {
            builder.append("执行结果：").append(context.getSqlExecutionResult());
        }
        return builder.toString();
    }

    private void persistAssistantMessage(WorkflowContext context, String answer) {
        historyRecorder.saveMessage(
                context,
                context.getRound().getRoundCode(),
                "ASSISTANT",
                AiChatActorType.AI.name(),
                AiChatMessageType.FINAL_ANSWER.name(),
                answer,
                AiChatContentFormat.MARKDOWN.name(),
                AiChatDisplayLevel.VISIBLE.name(),
                STATUS_SUCCESS,
                context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                context.getOrCreateUserMessageContext().getCurrentMessage() == null ? null : context.getOrCreateUserMessageContext().getCurrentMessage().getMessageCode(),
                null
        );
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

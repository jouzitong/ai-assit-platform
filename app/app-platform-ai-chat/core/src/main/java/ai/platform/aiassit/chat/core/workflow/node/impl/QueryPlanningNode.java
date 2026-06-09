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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 查询规划节点，负责抽取当前问题的执行意图并创建本轮执行上下文。
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
@Service
@Slf4j
public class QueryPlanningNode extends BaseWorkflowNode {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_FAILED = "FAILED";
    private static final String DEFAULT_SCENE = "ai-chat-query-planning";
    private static final String PLANNING_PROMPT = """
            你是一个智能问数工作流的查询规划节点。
            你需要根据用户当前问题和已有对话历史，输出一份可执行的规划文本。

            输出必须覆盖以下内容：
            1. 用户核心目标
            2. 关键分析维度
            3. 需要的知识上下文或数据口径
            4. SQL 生成应重点关注的过滤条件、聚合口径、排序方式
            5. 如果信息仍不足，需要明确指出风险点

            请直接输出中文规划文本，不要输出 JSON，不要补充寒暄。
            """;

    private final AiChatExecutionApi aiChatExecutionApi;
    private final AiMetaQueryApi aiMetaQueryApi;
    private final AiChatRoundService roundService;
    private final AiChatMessageService messageService;

    public QueryPlanningNode(AiChatExecutionApi aiChatExecutionApi,
                             AiMetaQueryApi aiMetaQueryApi,
                             AiChatRoundService roundService,
                             AiChatMessageService messageService) {
        this.aiChatExecutionApi = aiChatExecutionApi;
        this.aiMetaQueryApi = aiMetaQueryApi;
        this.roundService = roundService;
        this.messageService = messageService;
    }

    @Override
    protected NodeResult doExecute(WorkflowContext context) {
        AiChatQueryCommand command = context.getCommand();
        if (command == null) {
            return NodeResult.fail("command is required");
        }
        if (context.getSession() == null) {
            return NodeResult.fail("session is required");
        }
        if (!StringUtils.hasText(command.getMessage())) {
            return NodeResult.fail("message is required");
        }

        try {
            List<AiChatMessageDTO> historyMessages = new ArrayList<>(context.getSessionMessages());
            Long userId = resolveUserId(command.getUserId());

            AiChatRoundDTO round = createRound(context, userId, command);
            context.setRound(round);

            AiChatMessageDTO userMessage = persistMessage(
                    round.getRoundCode(),
                    context.getSession().getSessionCode(),
                    userId,
                    "USER",
                    command.getMessage(),
                    historyMessages.size() + 1
            );
            historyMessages.add(userMessage);
            context.setSessionMessages(historyMessages.stream()
                    .sorted(Comparator.comparing(AiChatMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                    .toList());

            ChatRequest planningRequest = buildPlanningRequest(command, historyMessages);
            ChatResponse planningResponse = aiChatExecutionApi.chat(planningRequest);
            String analysisResult = extractAnswer(planningResponse);

            context.setEngineRequest(planningRequest);
            context.setEngineResponse(planningResponse);
            context.setAnalysisResult(analysisResult);
            context.put("queryPlan", analysisResult);
            context.put("planningRequestId", planningResponse == null ? null : planningResponse.getRequestId());

            return NodeResult.success(null);
        } catch (Exception ex) {
            log.error("query planning failed, sessionCode={}", context.getSession().getSessionCode(), ex);
            finishRound(context.getRound(), context.getEngineRequest(), null, STATUS_FAILED);
            return NodeResult.fail(ex.getMessage());
        }
    }

    @Override
    public String type() {
        return "Query-Planning";
    }

    @Override
    public int order() {
        return 200;
    }

    private AiChatRoundDTO createRound(WorkflowContext context, Long userId, AiChatQueryCommand command) {
        AiChatRoundDTO round = new AiChatRoundDTO();
        round.setRoundCode(generateCode("round"));
        round.setSessionCode(context.getSession().getSessionCode());
        round.setUserId(userId);
        round.setModelCode(resolveActualModel(command.getApiModel()));
        round.setActualModel(resolveActualModel(command.getApiModel()));
        round.setStatus(STATUS_RUNNING);
        return roundService.add(round);
    }

    private ChatRequest buildPlanningRequest(AiChatQueryCommand command, List<AiChatMessageDTO> historyMessages) {
        ChatRequest request = new ChatRequest();
        request.setProvider(resolveProviderType(command));
        request.setModel(resolveActualModel(command.getApiModel()));

        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage systemMessage = new ChatMessage();
        systemMessage.setRole(MessageRole.SYSTEM);
        systemMessage.setContent(PLANNING_PROMPT);
        messages.add(systemMessage);

        if (!CollectionUtils.isEmpty(historyMessages)) {
            for (AiChatMessageDTO historyMessage : historyMessages) {
                ChatMessage message = new ChatMessage();
                message.setRole(resolveMessageRole(historyMessage.getRole()));
                message.setContent(historyMessage.getContent());
                messages.add(message);
            }
        }
        request.setMessages(messages);

        ChatOptions options = new ChatOptions();
        options.setMaxTokens(resolveMaxTokens(command.getApiModel()));
        options.setTimeoutMs(30_000);
        request.setOptions(options);

        RequestMeta meta = new RequestMeta();
        meta.setTraceId(StringUtils.hasText(command.getTraceId()) ? command.getTraceId() : generateCode("trace"));
        meta.setScene(StringUtils.hasText(command.getScene()) ? command.getScene() : DEFAULT_SCENE);
        request.setMeta(meta);
        return request;
    }

    private AiChatMessageDTO persistMessage(String roundCode,
                                            String sessionCode,
                                            Long userId,
                                            String role,
                                            String content,
                                            int sortNo) {
        AiChatMessageDTO message = new AiChatMessageDTO();
        message.setMessageCode(generateCode("msg"));
        message.setRoundCode(roundCode);
        message.setSessionCode(sessionCode);
        message.setRole(role);
        message.setContent(content);
        message.setSortNo(sortNo);
        return messageService.add(message);
    }

    private void finishRound(AiChatRoundDTO round, ChatRequest request, String actualModel, String status) {
        if (round == null || round.getId() == null) {
            return;
        }
        AiChatRoundDTO update = new AiChatRoundDTO();
        update.setStatus(status);
        update.setActualModel(actualModel);
        if (request != null) {
            update.setModelCode(request.getModel());
        }
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

    private ProviderType resolveProviderType(AiChatQueryCommand command) {
        AiModelConfigDTO config = findModelConfigByApiModel(command == null ? null : command.getApiModel());
        if (config != null && StringUtils.hasText(config.getProviderCode())) {
            return resolveProviderType(config.getProviderCode());
        }
        return ProviderType.DASHSCOPE;
    }

    private ProviderType resolveProviderType(String providerCode) {
        if (!StringUtils.hasText(providerCode)) {
            return ProviderType.DASHSCOPE;
        }
        try {
            return ProviderType.valueOf(providerCode.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return ProviderType.DASHSCOPE;
        }
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

    private MessageRole resolveMessageRole(String role) {
        if (!StringUtils.hasText(role)) {
            return MessageRole.USER;
        }
        try {
            return MessageRole.valueOf(role.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            return MessageRole.USER;
        }
    }

    private Long resolveUserId(Long userId) {
        return userId == null ? 0L : userId;
    }

    private String generateCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}

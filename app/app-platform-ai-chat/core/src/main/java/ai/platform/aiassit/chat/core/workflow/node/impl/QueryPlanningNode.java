package ai.platform.aiassit.chat.core.workflow.node.impl;

import ai.platform.aiassist.service.ai.api.AiChatExecutionApi;
import ai.platform.aiassist.service.ai.api.AiMetaQueryApi;
import ai.platform.aiassist.service.ai.api.dto.AiMetaQueryRequest;
import ai.platform.aiassist.service.ai.api.dto.AiModelConfigDTO;
import ai.platform.aiassist.service.ai.api.dto.ChatMessage;
import ai.platform.aiassist.service.ai.api.dto.ChatOptions;
import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
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
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
@Service
@Slf4j
public class QueryPlanningNode extends BaseWorkflowNode {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String DEFAULT_SCENE = "ai-chat-requirement-analysis";
    private static final String ANALYSIS_PROMPT = """
            你是一个需求分析助手。
            请基于用户当前输入和历史对话，提炼并输出：
            1. 用户当前核心需求
            2. 关键约束条件
            3. 已明确的信息
            4. 仍缺失但影响后续执行的信息
            结果请使用清晰的中文文本输出。
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
            long userId = resolveUserId(command.getUserId());

            AiChatRoundDTO round = createRound(context, userId, command);
            context.setRound(round);

            int nextSortNo = historyMessages.size() + 1;
            AiChatMessageDTO userMessage = persistMessage(
                    round.getRoundCode(),
                    context.getSession().getSessionCode(),
                    userId,
                    "USER",
                    command.getMessage(),
                    nextSortNo
            );
            historyMessages.add(userMessage);

            ChatRequest engineRequest = buildEngineRequest(command, historyMessages);
            context.setEngineRequest(engineRequest);

            ChatResponse engineResponse = aiChatExecutionApi.chat(engineRequest);
            context.setEngineResponse(engineResponse);

            String analysisResult = extractAnswer(engineResponse);
            context.setAnalysisResult(analysisResult);

            if (StringUtils.hasText(analysisResult)) {
                AiChatMessageDTO assistantMessage = persistMessage(
                        round.getRoundCode(),
                        context.getSession().getSessionCode(),
                        userId,
                        "ASSISTANT",
                        analysisResult,
                        historyMessages.size() + 1
                );
                historyMessages.add(assistantMessage);
            }

            context.setSessionMessages(historyMessages.stream()
                    .sorted(Comparator.comparing(AiChatMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                    .toList());
            context.put("analysisResult", analysisResult);

            finishRound(round, engineRequest, engineResponse == null ? null : engineResponse.getModel(), STATUS_SUCCESS);
            return NodeResult.success(null);
        } catch (Exception ex) {
            log.error("requirement analysis failed, sessionCode={}", context.getSession().getSessionCode(), ex);
            finishRound(context.getRound(), context.getEngineRequest(), null, STATUS_FAILED);
            return NodeResult.fail(ex.getMessage());
        }
    }

    @Override
    public String type() {
        return "Requirement-Analysis";
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

    private ChatRequest buildEngineRequest(AiChatQueryCommand command, List<AiChatMessageDTO> historyMessages) {
        ChatRequest engineRequest = new ChatRequest();
        engineRequest.setProvider(resolveProviderType(command));
        engineRequest.setModel(resolveActualModel(command.getApiModel()));

        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage systemMessage = new ChatMessage();
        systemMessage.setRole(MessageRole.SYSTEM);
        systemMessage.setContent(ANALYSIS_PROMPT);
        messages.add(systemMessage);

        if (!CollectionUtils.isEmpty(historyMessages)) {
            for (AiChatMessageDTO historyMessage : historyMessages) {
                ChatMessage chatMessage = new ChatMessage();
                chatMessage.setRole(resolveMessageRole(historyMessage.getRole()));
                chatMessage.setContent(historyMessage.getContent());
                messages.add(chatMessage);
            }
        }

        engineRequest.setMessages(messages);

        ChatOptions options = new ChatOptions();
        options.setMaxTokens(resolveMaxTokens(command.getApiModel()));
        options.setTimeoutMs(30_000);
        engineRequest.setOptions(options);

        RequestMeta meta = new RequestMeta();
        meta.setTraceId(StringUtils.hasText(command.getTraceId()) ? command.getTraceId() : generateCode("trace"));
        meta.setScene(StringUtils.hasText(command.getScene()) ? command.getScene() : DEFAULT_SCENE);
        engineRequest.setMeta(meta);
        return engineRequest;
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
        if (response == null || response.getOutputs() == null) {
            return "";
        }
        return response.getOutputs().stream()
                .filter(Objects::nonNull)
                .map(outputItem -> outputItem.getText())
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

    private long resolveUserId(Long userId) {
        return userId == null ? 0L : userId;
    }

    private String generateCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}

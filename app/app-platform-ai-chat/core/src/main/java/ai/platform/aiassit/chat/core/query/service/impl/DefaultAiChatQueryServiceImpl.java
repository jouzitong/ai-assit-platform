package ai.platform.aiassit.chat.core.query.service.impl;

import ai.platform.aiassist.service.ai.api.AiChatExecutionApi;
import ai.platform.aiassist.service.ai.api.AiMetaQueryApi;
import ai.platform.aiassist.service.ai.api.dto.AiMetaQueryRequest;
import ai.platform.aiassist.service.ai.api.dto.AiModelConfigDTO;
import ai.platform.aiassist.service.ai.api.dto.AiProviderConfigDTO;
import ai.platform.aiassist.service.ai.api.dto.ChatMessage;
import ai.platform.aiassist.service.ai.api.dto.ChatOptions;
import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.dto.RequestMeta;
import ai.platform.aiassist.service.ai.api.enums.MessageRole;
import ai.platform.aiassist.service.ai.api.enums.ProviderType;
import ai.platform.aiassist.service.ai.api.stream.ChatChunk;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryResponse;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryStreamEvent;
import ai.platform.aiassit.chat.core.query.service.AiChatQueryService;
import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.enums.AiChatActorType;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactStage;
import ai.platform.aiassit.chat.history.enums.AiChatArtifactType;
import ai.platform.aiassit.chat.history.enums.AiChatBusinessType;
import ai.platform.aiassit.chat.history.enums.AiChatContentFormat;
import ai.platform.aiassit.chat.history.enums.AiChatDisplayLevel;
import ai.platform.aiassit.chat.history.enums.AiChatMessageType;
import ai.platform.aiassit.chat.history.enums.AiChatRoundType;
import ai.platform.aiassit.chat.history.service.AiChatArtifactService;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import ai.platform.aiassit.chat.history.service.AiChatSessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
//@Service
@Deprecated
public class DefaultAiChatQueryServiceImpl implements AiChatQueryService {

    private static final String DEFAULT_SCENE = "ai-chat-query";
    private static final String DEFAULT_SESSION_NAME = "智能问数";
    private static final String DEFAULT_SYSTEM_PROMPT = "你是一个智能问数助手，回答时请优先给出结论、依据和下一步建议。";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final AiChatSessionService sessionService;
    private final AiChatRoundService roundService;
    private final AiChatMessageService messageService;
    private final AiChatArtifactService artifactService;
    private final AiMetaQueryApi aiMetaQueryApi;
    private final AiChatExecutionApi aiChatExecutionApi;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${app.ai-engine.url:http://127.0.0.1:13101/aiEngine}")
    private String aiEngineBaseUrl;

    public DefaultAiChatQueryServiceImpl(AiChatSessionService sessionService,
                                         AiChatRoundService roundService,
                                         AiChatMessageService messageService,
                                         AiChatArtifactService artifactService,
                                         AiMetaQueryApi aiMetaQueryApi,
                                         AiChatExecutionApi aiChatExecutionApi,
                                         ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.roundService = roundService;
        this.messageService = messageService;
        this.artifactService = artifactService;
        this.aiMetaQueryApi = aiMetaQueryApi;
        this.aiChatExecutionApi = aiChatExecutionApi;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public AiChatQueryResponse query(AiChatQueryCommand command) {
        QueryContext context = prepareContext(command, true);
        ChatResponse engineResponse = aiChatExecutionApi.chat(context.engineRequest).getData();

        String answer = extractAnswer(engineResponse);
        persistAssistantMessage(context, answer);
        finishRound(context.round, context.engineRequest, engineResponse.getModel(), STATUS_SUCCESS);

        AiChatQueryResponse response = new AiChatQueryResponse();
        response.setRequestId(engineResponse.getRequestId());
        response.setSessionCode(context.session.getSessionCode());
        response.setRoundCode(context.round.getRoundCode());
        response.setModelCode(context.engineRequest.getModel());
        response.setProviderCode(context.providerType.name());
        response.setAnswer(answer);
        response.setStatus(STATUS_SUCCESS);
        if (engineResponse.getUsage() != null) {
            response.setInputTokens(engineResponse.getUsage().getInputTokens());
            response.setOutputTokens(engineResponse.getUsage().getOutputTokens());
            response.setTotalTokens(engineResponse.getUsage().getTotalTokens());
        }
        if (engineResponse.getFinishReason() != null) {
            response.setFinishReason(engineResponse.getFinishReason().name());
        }
        response.setProviderMeta(engineResponse.getProviderMeta());
        return response;
    }

    @Override
    public SseEmitter queryStream(AiChatQueryCommand command) {
        SseEmitter emitter = new SseEmitter(0L);
        CompletableFuture.runAsync(() -> handleStream(command, emitter));
        return emitter;
    }

    private void handleStream(AiChatQueryCommand command, SseEmitter emitter) {
        QueryContext context = null;
        StringBuilder answerBuffer = new StringBuilder();
        try {
            context = prepareContext(command, true);

            AiChatQueryStreamEvent initEvent = new AiChatQueryStreamEvent();
            initEvent.setEventType("init");
            initEvent.setSessionCode(context.session.getSessionCode());
            initEvent.setRoundCode(context.round.getRoundCode());
            initEvent.setStatus(STATUS_RUNNING);
            emitter.send(SseEmitter.event().name("init").data(initEvent));

            String requestBody = objectMapper.writeValueAsString(context.engineRequest);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(aiEngineBaseUrl) + "/api/v1/ai/execution/chat/stream"))
                    .timeout(Duration.ofMillis(resolveTimeoutMs()))
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header("Accept", "text/event-stream;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<InputStream> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (httpResponse.statusCode() >= 400) {
                throw new IllegalStateException("ai engine stream failed, status=" + httpResponse.statusCode());
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(httpResponse.body(), StandardCharsets.UTF_8))) {
                String eventName = null;
                StringBuilder dataBuffer = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        if (StringUtils.hasText(dataBuffer)) {
                            processStreamEvent(eventName, dataBuffer.toString(), context, answerBuffer, emitter);
                        }
                        eventName = null;
                        dataBuffer = new StringBuilder();
                        continue;
                    }
                    if (line.startsWith("event:")) {
                        eventName = line.substring("event:".length()).trim();
                        continue;
                    }
                    if (line.startsWith("data:")) {
                        if (dataBuffer.length() > 0) {
                            dataBuffer.append('\n');
                        }
                        dataBuffer.append(line.substring("data:".length()).trim());
                    }
                }
                if (StringUtils.hasText(dataBuffer)) {
                    processStreamEvent(eventName, dataBuffer.toString(), context, answerBuffer, emitter);
                }
            }

            persistAssistantMessage(context, answerBuffer.toString());
            finishRound(context.round, context.engineRequest, context.engineRequest.getModel(), STATUS_SUCCESS);

            AiChatQueryStreamEvent doneEvent = new AiChatQueryStreamEvent();
            doneEvent.setEventType("complete");
            doneEvent.setRequestId(context.engineRequest.getMeta().getTraceId());
            doneEvent.setSessionCode(context.session.getSessionCode());
            doneEvent.setRoundCode(context.round.getRoundCode());
            doneEvent.setAnswer(answerBuffer.toString());
            doneEvent.setStatus(STATUS_SUCCESS);
            emitter.send(SseEmitter.event().name("complete").data(doneEvent));
            emitter.complete();
        } catch (Exception ex) {
            if (context != null) {
                finishRound(context.round, context.engineRequest, context.engineRequest == null ? null : context.engineRequest.getModel(), STATUS_FAILED);
            }
            AiChatQueryStreamEvent errorEvent = new AiChatQueryStreamEvent();
            errorEvent.setEventType("error");
            errorEvent.setSessionCode(context == null ? null : context.session.getSessionCode());
            errorEvent.setRoundCode(context == null ? null : context.round.getRoundCode());
            errorEvent.setStatus(STATUS_FAILED);
            errorEvent.setMessage(ex.getMessage());
            try {
                emitter.send(SseEmitter.event().name("error").data(errorEvent));
            } catch (IOException ioException) {
                log.warn("failed to send stream error event", ioException);
            }
            emitter.completeWithError(ex);
        }
    }

    private void processStreamEvent(String eventName,
                                    String data,
                                    QueryContext context,
                                    StringBuilder answerBuffer,
                                    SseEmitter emitter) throws IOException {
        if (!StringUtils.hasText(data)) {
            return;
        }
        if (eventName == null || "chunk".equalsIgnoreCase(eventName)) {
            ChatChunk chunk = objectMapper.readValue(data, ChatChunk.class);
            if (chunk != null && StringUtils.hasText(chunk.getDelta())) {
                answerBuffer.append(chunk.getDelta());
            }
            AiChatQueryStreamEvent streamEvent = new AiChatQueryStreamEvent();
            streamEvent.setEventType("chunk");
            streamEvent.setRequestId(chunk == null ? null : chunk.getRequestId());
            streamEvent.setSessionCode(context.session.getSessionCode());
            streamEvent.setRoundCode(context.round.getRoundCode());
            streamEvent.setDelta(chunk == null ? null : chunk.getDelta());
            streamEvent.setStatus(STATUS_RUNNING);
            emitter.send(SseEmitter.event().name("chunk").data(streamEvent));
            return;
        }

        AiChatQueryStreamEvent streamEvent = new AiChatQueryStreamEvent();
        streamEvent.setEventType(eventName);
        streamEvent.setSessionCode(context.session.getSessionCode());
        streamEvent.setRoundCode(context.round.getRoundCode());
        streamEvent.setStatus(STATUS_RUNNING);
        streamEvent.setMessage(data);
        emitter.send(SseEmitter.event().name(eventName).data(streamEvent));
    }

    private QueryContext prepareContext(AiChatQueryCommand command, boolean allowCreateSession) {
        if (command == null) {
            throw new IllegalArgumentException("request is required");
        }
        if (!StringUtils.hasText(command.getMessage())) {
            throw new IllegalArgumentException("message is required");
        }

        AiChatSessionDTO session = resolveSession(command, allowCreateSession);
        List<AiChatMessageDTO> historyMessages = loadMessages(session.getSessionCode(), command.getUserId());

        AiChatRoundDTO round = new AiChatRoundDTO();
        round.setRoundCode(generateCode("round"));
        round.setRoundType(resolveRoundType(command, historyMessages).name());
        round.setParentRoundCode(resolveParentRoundCode(session.getSessionCode(), command.getUserId()));
        round.setSessionCode(session.getSessionCode());
        round.setUserId(resolveUserId(command.getUserId()));
        round.setModelCode(resolveModelCode(command.getApiModel()));
        round.setActualModel(resolveActualModel(command.getApiModel()));
        round.setStatus(STATUS_RUNNING);
        AiChatRoundDTO createdRound = roundService.add(round);

        ChatRequest engineRequest = buildEngineRequest(command, historyMessages);
        QueryContext context = new QueryContext();
        context.session = session;
        context.round = createdRound;
        context.engineRequest = engineRequest;
        context.providerType = resolveProviderType(engineRequest.getProvider());
        context.historyMessages = historyMessages;
        context.currentUserMessage = persistMessage(
                context,
                "USER",
                AiChatActorType.HUMAN.name(),
                resolveUserMessageType(createdRound.getRoundType()),
                command.getMessage(),
                AiChatContentFormat.PLAIN_TEXT.name(),
                AiChatDisplayLevel.VISIBLE.name(),
                STATUS_SUCCESS,
                historyMessages.isEmpty() ? null : historyMessages.get(historyMessages.size() - 1).getMessageCode(),
                historyMessages.isEmpty() ? null : historyMessages.get(historyMessages.size() - 1).getMessageCode()
        );
        persistArtifact(context,
                AiChatArtifactType.MODEL_REQUEST_SNAPSHOT.name(),
                AiChatArtifactStage.UNDERSTAND.name(),
                "模型请求快照",
                engineRequest,
                AiChatContentFormat.JSON.name(),
                false,
                STATUS_SUCCESS,
                context.currentUserMessage.getMessageCode());
        return context;
    }

    private AiChatSessionDTO resolveSession(AiChatQueryCommand command, boolean allowCreateSession) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(command.getSessionCode());
        query.setCreatedBy(resolveUserId(command.getUserId()));
        AiChatSessionDTO session = sessionService.get(query);
        if (session != null) {
            return session;
        }
        if (!allowCreateSession) {
            throw new IllegalArgumentException("session not found");
        }
        AiChatSessionDTO created = new AiChatSessionDTO();
        created.setSessionCode(generateCode("session"));
        created.setUserId(resolveUserId(command.getUserId()));
        created.setBusinessType(defaultBusinessType(command.getBusinessType()));
        created.setSessionName(resolveSessionName(command.getSessionName(), command.getMessage()));
        return sessionService.add(created);
    }

    private List<AiChatMessageDTO> loadMessages(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        return messageService.queryAll(query).stream()
                .sorted(Comparator.comparing(AiChatMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private ChatRequest buildEngineRequest(AiChatQueryCommand command, List<AiChatMessageDTO> historyMessages) {
        ChatRequest engineRequest = new ChatRequest();
        engineRequest.setProvider(resolveProviderType(command));
        engineRequest.setModel(resolveActualModel(command.getApiModel()));

        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage systemMessage = new ChatMessage();
        systemMessage.setRole(MessageRole.SYSTEM);
        systemMessage.setContent(DEFAULT_SYSTEM_PROMPT);
        messages.add(systemMessage);

        if (!CollectionUtils.isEmpty(historyMessages)) {
            for (AiChatMessageDTO messageDTO : historyMessages) {
                ChatMessage chatMessage = new ChatMessage();
                chatMessage.setRole(resolveMessageRole(messageDTO.getRole()));
                chatMessage.setContent(messageDTO.getContent());
                messages.add(chatMessage);
            }
        }

        ChatMessage userMessage = new ChatMessage();
        userMessage.setRole(MessageRole.USER);
        userMessage.setContent(command.getMessage());
        messages.add(userMessage);
        engineRequest.setMessages(messages);

        ChatOptions options = new ChatOptions();
        options.setMaxTokens(resolveMaxTokens(command.getApiModel()));
        options.setTimeoutMs(resolveTimeoutMs());
        engineRequest.setOptions(options);

        RequestMeta meta = new RequestMeta();
        meta.setTraceId(StringUtils.hasText(command.getTraceId()) ? command.getTraceId() : generateCode("trace"));
        meta.setScene(StringUtils.hasText(command.getScene()) ? command.getScene() : DEFAULT_SCENE);
        meta.setExt(buildExt(command));
        engineRequest.setMeta(meta);
        return engineRequest;
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

    private ProviderType resolveProviderType(ProviderType providerType) {
        return providerType == null ? ProviderType.DASHSCOPE : providerType;
    }

    private String resolveModelCode(String modelCode) {
        if (!StringUtils.hasText(modelCode)) {
            return defaultModelCode();
        }
        return modelCode.trim();
    }

    private String resolveActualModel(String apiModel) {
        if (!StringUtils.hasText(apiModel)) {
            return defaultApiModel();
        }
        return apiModel.trim();
    }

    private ProviderType resolveProviderTypeFromConfig(String modelCode) {
        AiModelConfigDTO config = findModelConfig(modelCode);
        if (config != null && StringUtils.hasText(config.getProviderCode())) {
            return resolveProviderType(config.getProviderCode());
        }
        return ProviderType.DASHSCOPE;
    }

    private AiModelConfigDTO findModelConfig(String modelCode) {
        if (!StringUtils.hasText(modelCode)) {
            return null;
        }
        AiMetaQueryRequest request = new AiMetaQueryRequest();
        request.setModelCode(modelCode);
        request.setEnabled(Boolean.TRUE);
        List<AiModelConfigDTO> configs = aiMetaQueryApi.listModels(request);
        return configs.isEmpty() ? null : configs.get(0);
    }

    private AiProviderConfigDTO findProviderConfig(String providerCode) {
        if (!StringUtils.hasText(providerCode)) {
            return null;
        }
        AiMetaQueryRequest request = new AiMetaQueryRequest();
        request.setProviderCode(providerCode);
        request.setEnabled(Boolean.TRUE);
        List<AiProviderConfigDTO> configs = aiMetaQueryApi.listProviders(request);
        return configs.isEmpty() ? null : configs.get(0);
    }

    private Integer resolveMaxTokens(String apiModel) {
        AiModelConfigDTO config = findModelConfigByApiModel(apiModel);
        return config == null ? 1024 : config.getMaxOutputTokens();
    }

    private Integer resolveTimeoutMs() {
        return 30_000;
    }

    private AiChatBusinessType defaultBusinessType(AiChatBusinessType businessType) {
        return businessType == null ? AiChatBusinessType.GENERAL : businessType;
    }

    private String resolveSessionName(String sessionName, String prompt) {
        if (StringUtils.hasText(sessionName)) {
            return sessionName.trim();
        }
        if (StringUtils.hasText(prompt)) {
            String trimmed = prompt.trim();
            return trimmed.length() <= 24 ? trimmed : trimmed.substring(0, 24);
        }
        return DEFAULT_SESSION_NAME;
    }

    private long resolveUserId(Long userId) {
        return userId == null ? 0L : userId;
    }

    private void persistAssistantMessage(QueryContext context, String answer) {
        if (!StringUtils.hasText(answer)) {
            return;
        }
        persistMessage(
                context,
                "ASSISTANT",
                AiChatActorType.AI.name(),
                AiChatMessageType.FINAL_ANSWER.name(),
                answer,
                AiChatContentFormat.MARKDOWN.name(),
                AiChatDisplayLevel.VISIBLE.name(),
                STATUS_SUCCESS,
                context.currentUserMessage == null ? null : context.currentUserMessage.getMessageCode(),
                context.currentUserMessage == null ? null : context.currentUserMessage.getMessageCode()
        );
        persistArtifact(context,
                AiChatArtifactType.MODEL_RESPONSE_SNAPSHOT.name(),
                AiChatArtifactStage.RENDER.name(),
                "模型响应快照",
                answer,
                AiChatContentFormat.MARKDOWN.name(),
                true,
                STATUS_SUCCESS,
                context.currentUserMessage == null ? null : context.currentUserMessage.getMessageCode());
    }

    private AiChatMessageDTO persistMessage(QueryContext context,
                                String role,
                                String actorType,
                                String messageType,
                                String content,
                                String contentFormat,
                                String displayLevel,
                                String status,
                                String parentMessageCode,
                                String sourceMessageCode) {
        AiChatMessageDTO message = new AiChatMessageDTO();
        message.setMessageCode(generateCode("msg"));
        message.setRoundCode(context.round.getRoundCode());
        message.setSessionCode(context.session.getSessionCode());
        message.setRole(role);
        message.setActorType(actorType);
        message.setMessageType(messageType);
        message.setContentFormat(contentFormat);
        message.setDisplayLevel(displayLevel);
        message.setStatus(status);
        message.setParentMessageCode(parentMessageCode);
        message.setSourceMessageCode(sourceMessageCode);
        message.setContent(content);
        message.setSortNo(nextMessageSortNo(context));
        AiChatMessageDTO created = messageService.add(message);
        context.historyMessages.add(created);
        return created;
    }

    private void persistArtifact(QueryContext context,
                                 String artifactType,
                                 String stage,
                                 String title,
                                 Object content,
                                 String contentFormat,
                                 boolean visible,
                                 String status,
                                 String relatedMessageCode) {
        AiChatArtifactDTO artifact = new AiChatArtifactDTO();
        artifact.setArtifactCode(generateCode("artifact"));
        artifact.setSessionCode(context.session.getSessionCode());
        artifact.setRoundCode(context.round.getRoundCode());
        artifact.setUserId(context.round.getUserId());
        artifact.setRelatedMessageCode(relatedMessageCode);
        artifact.setArtifactType(artifactType);
        artifact.setStage(stage);
        artifact.setProducerType(visible ? AiChatActorType.AI.name() : AiChatActorType.SYSTEM.name());
        artifact.setVisibleFlag(visible);
        artifact.setTitle(title);
        artifact.setContent(stringifyContent(content));
        artifact.setContentFormat(contentFormat);
        artifact.setStatus(status);
        artifact.setSeqNo(nextArtifactSortNo(context));
        artifactService.add(artifact);
    }

    private AiChatHistoryQueryRequest buildHistoryQuery(String sessionCode, Long userId) {
        AiChatHistoryQueryRequest query = new AiChatHistoryQueryRequest();
        query.setSessionCode(sessionCode);
        query.setCreatedBy(userId);
        return query;
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

    private String generateCode(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }

    private String defaultModelCode() {
        AiModelConfigDTO config = findFirstEnabledModelConfig();
        return config != null && StringUtils.hasText(config.getModelCode())
                ? config.getModelCode()
                : "qwen-math-turbo";
    }

    private String defaultApiModel() {
        AiModelConfigDTO config = findFirstEnabledModelConfig();
        if (config == null) {
            return defaultModelCode();
        }
        if (StringUtils.hasText(config.getApiModel())) {
            return config.getApiModel();
        }
        return StringUtils.hasText(config.getModelCode()) ? config.getModelCode() : defaultModelCode();
    }

    private AiModelConfigDTO findFirstEnabledModelConfig() {
        AiMetaQueryRequest request = new AiMetaQueryRequest();
        request.setEnabled(Boolean.TRUE);
        List<AiModelConfigDTO> configs = aiMetaQueryApi.listModels(request);
        return configs.isEmpty() ? null : configs.get(0);
    }

    private ProviderType resolveProviderType(AiChatQueryCommand command) {
        if (command == null) {
            return ProviderType.DASHSCOPE;
        }
        AiModelConfigDTO config = findModelConfigByApiModel(command.getApiModel());
        if (config != null && StringUtils.hasText(config.getProviderCode())) {
            return resolveProviderType(config.getProviderCode());
        }
        AiProviderConfigDTO providerConfig = findProviderConfig("DASHSCOPE");
        if (providerConfig != null) {
            return ProviderType.DASHSCOPE;
        }
        return ProviderType.DASHSCOPE;
    }

    private AiModelConfigDTO findModelConfigByApiModel(String apiModel) {
        if (!StringUtils.hasText(apiModel)) {
            return findFirstEnabledModelConfig();
        }
        AiMetaQueryRequest request = new AiMetaQueryRequest();
        request.setEnabled(Boolean.TRUE);
        return aiMetaQueryApi.listModels(request).stream()
                .filter(Objects::nonNull)
                .filter(config -> StringUtils.hasText(config.getApiModel()))
                .filter(config -> apiModel.trim().equals(config.getApiModel().trim()))
                .findFirst()
                .orElse(null);
    }

    private java.util.Map<String, Object> buildExt(AiChatQueryCommand command) {
        java.util.Map<String, Object> ext = new java.util.HashMap<>();
        if (command.getExt() != null) {
            ext.putAll(command.getExt());
        }
        if (!CollectionUtils.isEmpty(command.getAttachments())) {
            ext.put("attachments", command.getAttachments());
        }
        if (!CollectionUtils.isEmpty(command.getTools())) {
            ext.put("tools", command.getTools());
        }
        return ext;
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

    private String trimTrailingSlash(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("/+$", "");
    }

    private int nextMessageSortNo(QueryContext context) {
        return CollectionUtils.isEmpty(context.historyMessages) ? 1 : context.historyMessages.size() + 1;
    }

    private int nextArtifactSortNo(QueryContext context) {
        return artifactService.queryAll(buildHistoryQuery(context.session.getSessionCode(), context.round.getUserId())).size() + 1;
    }

    private AiChatRoundType resolveRoundType(AiChatQueryCommand command, List<AiChatMessageDTO> historyMessages) {
        Object extValue = command == null || command.getExt() == null ? null : command.getExt().get("roundType");
        if (extValue instanceof String str && StringUtils.hasText(str)) {
            try {
                return AiChatRoundType.valueOf(str.trim().toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                // fall through
            }
        }
        if (CollectionUtils.isEmpty(historyMessages)) {
            return AiChatRoundType.USER_QUERY;
        }
        AiChatMessageDTO lastMessage = historyMessages.get(historyMessages.size() - 1);
        if (AiChatMessageType.ASSISTANT_QUESTION.name().equals(lastMessage.getMessageType())) {
            return AiChatRoundType.CLARIFICATION;
        }
        return AiChatRoundType.FOLLOW_UP;
    }

    private String resolveParentRoundCode(String sessionCode, Long userId) {
        List<AiChatRoundDTO> rounds = roundService.queryAll(buildHistoryQuery(sessionCode, userId));
        if (CollectionUtils.isEmpty(rounds)) {
            return null;
        }
        return rounds.get(rounds.size() - 1).getRoundCode();
    }

    private String resolveUserMessageType(String roundType) {
        if (AiChatRoundType.CLARIFICATION.name().equals(roundType)) {
            return AiChatMessageType.USER_CLARIFICATION.name();
        }
        return AiChatMessageType.USER_INPUT.name();
    }

    private String stringifyContent(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String str) {
            return str;
        }
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception ex) {
            return String.valueOf(content);
        }
    }

    private static final class QueryContext {
        private AiChatSessionDTO session;
        private AiChatRoundDTO round;
        private ChatRequest engineRequest;
        private ProviderType providerType;
        private AiChatMessageDTO currentUserMessage;
        private List<AiChatMessageDTO> historyMessages = new ArrayList<>();
    }
}

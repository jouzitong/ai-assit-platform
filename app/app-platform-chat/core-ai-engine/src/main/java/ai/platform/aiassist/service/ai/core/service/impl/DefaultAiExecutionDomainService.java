package ai.platform.aiassist.service.ai.core.service.impl;

import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassist.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassist.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.RerankRequest;
import ai.platform.aiassist.service.ai.api.dto.RerankResponse;
import ai.platform.aiassist.service.ai.api.enums.ProviderType;
import ai.platform.aiassist.service.ai.api.stream.ChatStreamObserver;
import ai.platform.aiassist.service.ai.core.properties.AiCoreProperties;
import ai.platform.aiassist.service.ai.core.convert.AiProviderRequestMapper;
import ai.platform.aiassist.service.ai.core.service.AiExecutionDomainService;
import ai.platform.aiassist.service.ai.core.validator.AiRequestValidator;
import ai.platform.aiassist.service.ai.spi.AiChatService;
import ai.platform.aiassist.service.ai.spi.KnowledgeService;
import org.springframework.beans.factory.ObjectProvider;
import org.arthena.framework.common.thread.schedule.ScheduleMonitor;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class DefaultAiExecutionDomainService implements AiExecutionDomainService {

    private final Map<ProviderType, AiChatService> chatServices = new EnumMap<>(ProviderType.class);
    private final Map<ProviderType, KnowledgeService> knowledgeServices = new EnumMap<>(ProviderType.class);
    private final AiCoreProperties properties;
    private final AiRequestValidator validator;
    private final AiProviderRequestMapper requestMapper;
    private final ScheduleMonitor scheduleMonitor;

    public DefaultAiExecutionDomainService(List<AiChatService> aiChatServices,
                                           List<KnowledgeService> knowledgeServices,
                                           AiCoreProperties properties,
                                           AiRequestValidator validator,
                                           AiProviderRequestMapper requestMapper,
                                           ObjectProvider<ScheduleMonitor> scheduleMonitorProvider) {
        for (AiChatService service : aiChatServices) {
            this.chatServices.put(service.providerType(), service);
        }
        for (KnowledgeService service : knowledgeServices) {
            this.knowledgeServices.put(service.providerType(), service);
        }
        this.properties = properties;
        this.validator = validator;
        this.requestMapper = requestMapper;
        this.scheduleMonitor = scheduleMonitorProvider.getIfAvailable();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        validator.validateChat(request);
        return resolveChatService(request.getProvider())
                .chat(requestMapper.mapChat(request, properties));
    }

    @Override
    public void chatStream(ChatRequest request, ChatStreamObserver observer) {
        validator.validateChat(request);
        if (observer == null) {
            throw new IllegalArgumentException("chatStream observer must not be null");
        }
        resolveChatService(request.getProvider()).chatStream(requestMapper.mapChat(request, properties), observer);
    }

    @Override
    public void chatStreamAsync(ChatRequest request, ChatStreamObserver observer) {
        if (scheduleMonitor == null) {
            CompletableFuture.runAsync(() -> chatStream(request, observer));
            return;
        }
        AtomicReference<String> taskIdRef = new AtomicReference<>();
        String taskId = scheduleMonitor.schedule(() -> {
            try {
                chatStream(request, observer);
            } finally {
                String id = taskIdRef.get();
                if (id != null) {
                    scheduleMonitor.cancel(id);
                }
            }
        }, 1L, TimeUnit.MILLISECONDS);
        taskIdRef.set(taskId);
    }

    @Override
    public EmbedResponse embed(EmbedRequest request) {
        validator.validateEmbed(request);
        return resolveKnowledgeService(request.getProvider()).embed(requestMapper.mapEmbed(request, properties));
    }

    @Override
    public RerankResponse rerank(RerankRequest request) {
        validator.validateRerank(request);
        return resolveKnowledgeService(request.getProvider()).rerank(requestMapper.mapRerank(request, properties));
    }

    @Override
    public KbUpsertResponse kbUpsert(KbUpsertRequest request) {
        validator.validateKbUpsert(request);
        return resolveKnowledgeService(null).kbUpsert(requestMapper.mapKbUpsert(request));
    }

    @Override
    public KbDeleteResponse kbDelete(KbDeleteRequest request) {
        validator.validateKbDelete(request);
        return resolveKnowledgeService(null).kbDelete(requestMapper.mapKbDelete(request));
    }

    @Override
    public KbSearchResponse kbSearch(KbSearchRequest request) {
        validator.validateKbSearch(request);
        return resolveKnowledgeService(null).kbSearch(requestMapper.mapKbSearch(request));
    }

    private AiChatService resolveChatService(ProviderType requestedProvider) {
        ProviderType providerType = requestedProvider;
        if (providerType == null) {
            if (properties.isStrictProvider()) {
                throw new IllegalArgumentException("provider is required when ai.core.strict-provider=true");
            }
            providerType = properties.getDefaultProvider();
        }

        AiChatService service = chatServices.get(providerType);
        if (service == null) {
            throw new IllegalStateException("AI chat service not found or not enabled: " + providerType);
        }
        return service;
    }

    private KnowledgeService resolveKnowledgeService(ProviderType requestedProvider) {
        ProviderType providerType = requestedProvider;
        if (providerType == null) {
            if (properties.isStrictProvider()) {
                throw new IllegalArgumentException("provider is required when ai.core.strict-provider=true");
            }
            providerType = properties.getDefaultProvider();
        }

        KnowledgeService service = knowledgeServices.get(providerType);
        if (service == null) {
            throw new IllegalStateException("knowledge service not found or not enabled: " + providerType);
        }
        return service;
    }
}

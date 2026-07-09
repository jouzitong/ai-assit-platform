package ai.platform.aiassit.execution.service.impl;

import ai.platform.aiassit.service.ai.api.dto.ChatRequest;
import ai.platform.aiassit.service.ai.api.dto.ChatResponse;
import ai.platform.aiassit.service.ai.api.enums.ProviderType;
import ai.platform.aiassit.service.ai.api.stream.ChatStreamObserver;
import ai.platform.aiassit.execution.properties.AiCoreProperties;
import ai.platform.aiassit.execution.convert.AiProviderRequestMapper;
import ai.platform.aiassit.execution.service.AiExecutionDomainService;
import ai.platform.aiassit.execution.validator.AiRequestValidator;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.spi.AiChatService;
import org.arthena.framework.common.exception.BizException;
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
    private final AiCoreProperties properties;
    private final AiRequestValidator validator;
    private final AiProviderRequestMapper requestMapper;
    private final ScheduleMonitor scheduleMonitor;

    public DefaultAiExecutionDomainService(List<AiChatService> aiChatServices,
                                           AiCoreProperties properties,
                                           AiRequestValidator validator,
                                           AiProviderRequestMapper requestMapper,
                                           ObjectProvider<ScheduleMonitor> scheduleMonitorProvider) {
        for (AiChatService service : aiChatServices) {
            this.chatServices.put(service.providerType(), service);
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
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_QUERY_COMMAND);
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

    private AiChatService resolveChatService(ProviderType requestedProvider) {
        ProviderType providerType = requestedProvider;
        if (providerType == null) {
            if (properties.isStrictProvider()) {
                throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_PROVIDER);
            }
            providerType = properties.getDefaultProvider();
        }

        AiChatService service = chatServices.get(providerType);
        if (service == null) {
            throw BizException.of(AiChatBizCodeConstant.AI_CHAT_SERVICE_NOT_FOUND, providerType);
        }
        return service;
    }
}

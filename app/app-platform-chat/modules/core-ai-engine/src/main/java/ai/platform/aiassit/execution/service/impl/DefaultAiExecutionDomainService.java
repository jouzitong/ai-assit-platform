package ai.platform.aiassit.execution.service.impl;

import ai.platform.aiassit.service.ai.api.dto.ChatRequest;
import ai.platform.aiassit.service.ai.api.dto.ChatResponse;
import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import ai.platform.aiassit.service.ai.api.stream.ChatStreamObserver;
import ai.platform.aiassit.execution.properties.AiCoreProperties;
import ai.platform.aiassit.execution.convert.AiProviderRequestMapper;
import ai.platform.aiassit.execution.service.AiExecutionDomainService;
import ai.platform.aiassit.execution.validator.AiRequestValidator;
import ai.platform.aiassit.model.entity.dto.AiModelConfigDTO;
import ai.platform.aiassit.model.service.AiModelConfigService;
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

    private final Map<AiChatClientType, AiChatService> chatServices = new EnumMap<>(AiChatClientType.class);
    private final AiCoreProperties properties;
    private final AiRequestValidator validator;
    private final AiProviderRequestMapper requestMapper;
    private final AiModelConfigService modelConfigService;
    private final ScheduleMonitor scheduleMonitor;

    public DefaultAiExecutionDomainService(List<AiChatService> aiChatServices,
                                           AiCoreProperties properties,
                                           AiRequestValidator validator,
                                           AiProviderRequestMapper requestMapper,
                                           AiModelConfigService modelConfigService,
                                           ObjectProvider<ScheduleMonitor> scheduleMonitorProvider) {
        for (AiChatService service : aiChatServices) {
            this.chatServices.put(service.chatClientType(), service);
        }
        this.properties = properties;
        this.validator = validator;
        this.requestMapper = requestMapper;
        this.modelConfigService = modelConfigService;
        this.scheduleMonitor = scheduleMonitorProvider.getIfAvailable();
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        validator.validateChat(request);
        ResolvedChatRequest resolved = resolveChatRequest(request);
        return resolveChatService(resolved.clientType())
                .chat(requestMapper.mapChat(request, properties, resolved.modelConfig()));
    }

    @Override
    public void chatStream(ChatRequest request, ChatStreamObserver observer) {
        validator.validateChat(request);
        if (observer == null) {
            throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_QUERY_COMMAND);
        }
        ResolvedChatRequest resolved = resolveChatRequest(request);
        resolveChatService(resolved.clientType()).chatStream(requestMapper.mapChat(request, properties, resolved.modelConfig()), observer);
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

    private AiChatService resolveChatService(AiChatClientType requestedClientType) {
        AiChatClientType clientType = requestedClientType;
        if (clientType == null) {
            if (properties.isStrictClientType()) {
                throw BizException.illegalParam(AiChatBizCodeConstant.REQUIRED_CHAT_CLIENT_TYPE);
            }
            clientType = properties.getDefaultChatClientType();
        }

        AiChatService service = chatServices.get(clientType);
        if (service == null) {
            throw BizException.of(AiChatBizCodeConstant.AI_CHAT_SERVICE_NOT_FOUND, clientType);
        }
        return service;
    }

    private ResolvedChatRequest resolveChatRequest(ChatRequest request) {
        String modelCode = request.getModelCode();
        AiModelConfigDTO modelConfig = modelConfigService.getByModelCode(modelCode);
        if (modelConfig != null) {
            if (!Boolean.TRUE.equals(modelConfig.getEnabled())) {
                throw BizException.of(AiChatBizCodeConstant.MODEL_CONFIG_NOT_FOUND, modelCode);
            }
        }

        AiChatClientType clientType = request.getClientType();
        if (clientType == null && modelConfig != null) {
            clientType = modelConfig.getClientType();
        }
        return new ResolvedChatRequest(clientType, modelConfig);
    }

    private record ResolvedChatRequest(AiChatClientType clientType, AiModelConfigDTO modelConfig) {
    }
}

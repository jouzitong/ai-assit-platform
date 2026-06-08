package ai.platform.aiassit.chat.core.query.controller.impl;

import ai.platform.aiassit.chat.core.query.service.AiChatQueryService;
import ai.platform.aiassit.chat.core.query.controller.AiChatQueryApi;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryRequest;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryResponse;
import ai.platform.aiassit.chat.core.query.service.AiChatQueryCommand;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/chat")
@AllArgsConstructor
public class AiChatQueryController implements AiChatQueryApi {

    private static final String DEFAULT_SCENE = "ai-chat-query";

    private final AiChatQueryService service;

    @Override
    @PostMapping("/query")
    public AiChatQueryResponse query(@RequestBody AiChatQueryRequest request) {
        return service.query(buildCommand(request));
    }

    @Override
    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryStream(@RequestBody AiChatQueryRequest request) {
        return service.queryStream(buildCommand(request));
    }

    private AiChatQueryCommand buildCommand(AiChatQueryRequest request) {
        AiChatQueryCommand command = new AiChatQueryCommand();
        command.setSessionCode(request == null ? null : request.getSessionCode());
        command.setApiModel(request == null ? null : request.getApiModel());
        command.setMessage(request == null ? null : request.getMessage());
        command.setAttachments(request == null || request.getAttachments() == null ? List.of() : request.getAttachments());
        command.setTools(request == null || request.getTools() == null ? List.of() : request.getTools());
        command.setExt(request == null || request.getExt() == null ? Map.of() : request.getExt());
        command.setScene(DEFAULT_SCENE);
        command.setTraceId(resolveTraceId());
        command.setUserId(resolveUserId());
        command.setSessionName(resolveSessionName(command.getMessage()));
        return command;
    }

    private String resolveTraceId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String traceId = attributes.getRequest().getHeader("traceId");
            if (traceId == null || traceId.isBlank()) {
                traceId = attributes.getRequest().getHeader("X-Trace-Id");
            }
            if (traceId != null && !traceId.isBlank()) {
                return traceId.trim();
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private Long resolveUserId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            String userId = attributes.getRequest().getHeader("X-User-Id");
            if (userId == null || userId.isBlank()) {
                userId = attributes.getRequest().getHeader("userId");
            }
            if (userId != null && !userId.isBlank()) {
                try {
                    return Long.parseLong(userId.trim());
                } catch (NumberFormatException ignored) {
                    return 0L;
                }
            }
        }
        return 0L;
    }

    private String resolveSessionName(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= 24 ? trimmed : trimmed.substring(0, 24);
    }
}

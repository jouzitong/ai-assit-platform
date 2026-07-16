package ai.platform.aiassit.conversation.controller;

import ai.platform.aiassit.conversation.dto.protocol.RenderArtifactResponse;
import ai.platform.aiassit.conversation.dto.protocol.RoundThinkingResponse;
import ai.platform.aiassit.conversation.protocol.ChatTransportProtocolAdapter;
import ai.platform.aiassit.conversation.protocol.dto.ChatTransportRequest;
import ai.platform.aiassit.conversation.runtime.ConversationRunManager;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunSnapshot;
import ai.platform.aiassit.conversation.service.ConversationProtocolQueryService;
import ai.platform.aiassit.conversation.support.ConversationCommandFactory;
import ai.platform.aiassit.conversation.support.ConversationRequestContextResolver;
import ai.platform.aiassit.conversation.transport.sse.ProtocolSseConversationTransport;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatTransportProtocolController {

    private final ProtocolSseConversationTransport sseTransport;
    private final ConversationCommandFactory commandFactory;
    private final ConversationRequestContextResolver contextResolver;
    private final ConversationProtocolQueryService queryService;
    private final ConversationRunManager runManager;
    private final ChatTransportProtocolAdapter protocolAdapter;

    public ChatTransportProtocolController(ProtocolSseConversationTransport sseTransport,
                                           ConversationCommandFactory commandFactory,
                                           ConversationRequestContextResolver contextResolver,
                                           ConversationProtocolQueryService queryService,
                                           ConversationRunManager runManager,
                                           ChatTransportProtocolAdapter protocolAdapter) {
        this.sseTransport = sseTransport;
        this.commandFactory = commandFactory;
        this.contextResolver = contextResolver;
        this.queryService = queryService;
        this.runManager = runManager;
        this.protocolAdapter = protocolAdapter;
    }

    @PostMapping(value = "/sessions/{sessionCode}/rounds/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String sessionCode, @RequestBody ChatTransportRequest request) {
        ConversationQueryCommand command = commandFactory.fromProtocol(
                request, sessionCode, contextResolver.currentUserId(), contextResolver.traceId(),
                contextResolver.canOverrideModel());
        return sseTransport.start(command);
    }

    @PostMapping(value = "/rounds/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNewSession(@RequestBody ChatTransportRequest request) {
        ConversationQueryCommand command = commandFactory.fromProtocol(
                request, null, contextResolver.currentUserId(), contextResolver.traceId(),
                contextResolver.canOverrideModel());
        return sseTransport.start(command);
    }

    @PostMapping(value = "/stream/reconnect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter reconnect(@RequestBody ChatTransportRequest request,
                                @RequestHeader(value = "Last-Event-ID", required = false) String headerLastEventId) {
        ChatTransportRequest reconnectRequest = request == null ? new ChatTransportRequest() : request;
        if (!StringUtils.hasText(reconnectRequest.getLastEventId())) {
            reconnectRequest.setLastEventId(headerLastEventId);
        }
        return sseTransport.reconnect(
                reconnectRequest,
                contextResolver.currentUserId(),
                contextResolver.traceId());
    }

    @GetMapping("/sessions/{sessionCode}/rounds/{roundCode}/thinking")
    public RoundThinkingResponse thinking(@PathVariable String sessionCode, @PathVariable String roundCode) {
        return queryService.thinkingDetail(sessionCode, roundCode, contextResolver.currentUserId());
    }

    @GetMapping("/render-artifacts/{codeRef}")
    public RenderArtifactResponse renderArtifact(@PathVariable String codeRef) {
        return queryService.renderArtifact(codeRef, contextResolver.currentUserId());
    }

    @GetMapping("/runs/{runId}")
    public Map<String, Object> runStatus(@PathVariable String runId) {
        return runManager.find(runId, null, null, contextResolver.currentUserId())
                .map(this::runStatus)
                .orElseGet(Map::of);
    }

    @PostMapping("/runs/{runId}/stop")
    public Boolean stop(@PathVariable String runId) {
        return runManager.cancel(runId, null, null, contextResolver.currentUserId());
    }

    private Map<String, Object> runStatus(ConversationRunSnapshot run) {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("runId", run.runId());
        status.put("requestId", run.requestId());
        status.put("sessionCode", run.sessionCode());
        status.put("roundCode", run.roundCode());
        status.put("status", run.state() == null ? null : run.state().name().toLowerCase());
        status.put("active", run.active());
        status.put("createdAt", run.createdAt());
        status.put("startedAt", run.startedAt());
        status.put("finishedAt", run.finishedAt());
        if (StringUtils.hasText(run.error())) {
            Map<String, Object> errorInfo = protocolAdapter.failureError(
                    run.error(), run.requestId(), "RUNTIME", Map.of());
            status.put("error", errorInfo.get("detail"));
            status.put("errorInfo", errorInfo);
        } else {
            status.put("error", null);
        }
        return status;
    }
}

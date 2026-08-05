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

/**
 * 新版聊天传输协议接口。
 *
 * <p>将会话执行、SSE 事件重连、运行状态和产物查询统一为面向前端的协议层；用户身份和模型覆盖权限由当前请求上下文确定。</p>
 */
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

    /**
     * 在已有会话中创建新轮次并以 SSE 推送执行事件。
     *
     * @param sessionCode 要续聊的会话编码
     * @param request     聊天协议请求体，包含消息、模型及运行选项
     * @return SSE 事件流，依次推送轮次初始化、思考、回答和结束事件
     */
    @PostMapping(value = "/sessions/{sessionCode}/rounds/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String sessionCode, @RequestBody ChatTransportRequest request) {
        ConversationQueryCommand command = commandFactory.fromProtocol(
                request, sessionCode, contextResolver.currentUserId(), contextResolver.traceId(),
                contextResolver.canOverrideModel());
        return sseTransport.start(command);
    }

    /**
     * 创建新会话并以 SSE 推送首轮聊天执行事件。
     *
     * @param request 聊天协议请求体，包含首条消息、模型及运行选项
     * @return SSE 事件流，包含新会话和新轮次的执行过程
     */
    @PostMapping(value = "/rounds/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNewSession(@RequestBody ChatTransportRequest request) {
        ConversationQueryCommand command = commandFactory.fromProtocol(
                request, null, contextResolver.currentUserId(), contextResolver.traceId(),
                contextResolver.canOverrideModel());
        return sseTransport.start(command);
    }

    /**
     * 在已有会话中执行系统设置助手轮次并推送 SSE 事件。
     *
     * @param sessionCode 系统设置助手所属会话编码
     * @param request     设置助手协议请求体，包含用户指令和上下文
     * @return SSE 事件流，包含设置分析和执行结果
     */
    @PostMapping(value = "/settings-assistant/sessions/{sessionCode}/rounds/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSettingsAssistant(@PathVariable String sessionCode,
                                              @RequestBody ChatTransportRequest request) {
        ConversationQueryCommand command = commandFactory.fromSettingsAssistantProtocol(
                request, sessionCode, contextResolver.currentUserId(), contextResolver.traceId(),
                contextResolver.canOverrideModel());
        return sseTransport.start(command);
    }

    /**
     * 创建新会话并执行系统设置助手首轮任务。
     *
     * @param request 设置助手协议请求体，包含用户指令和运行上下文
     * @return SSE 事件流，包含新会话的设置助手执行过程
     */
    @PostMapping(value = "/settings-assistant/rounds/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNewSettingsAssistantSession(@RequestBody ChatTransportRequest request) {
        ConversationQueryCommand command = commandFactory.fromSettingsAssistantProtocol(
                request, null, contextResolver.currentUserId(), contextResolver.traceId(),
                contextResolver.canOverrideModel());
        return sseTransport.start(command);
    }

    /**
     * 重新订阅指定聊天运行的 SSE 事件流。
     *
     * @param request           重连请求体，包含运行、会话或轮次定位信息及最后事件游标
     * @param headerLastEventId HTTP 标头中的最后事件标识，未写入请求体时作为回放游标
     * @return SSE 事件流，从已确认的游标继续回放并订阅后续事件
     */
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

    /**
     * 查询某轮聊天已持久化的思考过程。
     *
     * @param sessionCode 会话编码
     * @param roundCode   轮次编码
     * @return 面向前端展示的结构化思考与活动信息
     */
    @GetMapping("/sessions/{sessionCode}/rounds/{roundCode}/thinking")
    public RoundThinkingResponse thinking(@PathVariable String sessionCode, @PathVariable String roundCode) {
        return queryService.thinkingDetail(sessionCode, roundCode, contextResolver.currentUserId());
    }

    /**
     * 查询聊天轮次产出的渲染类产物。
     *
     * @param codeRef 产物引用编码
     * @return 渲染产物的内容、布局及可展示元数据
     */
    @GetMapping("/render-artifacts/{codeRef}")
    public RenderArtifactResponse renderArtifact(@PathVariable String codeRef) {
        return queryService.renderArtifact(codeRef, contextResolver.currentUserId());
    }

    /**
     * 查询聊天运行的实时或最终状态。
     *
     * @param runId 聊天运行标识
     * @return 运行归属、会话轮次、生命周期状态、时间戳及已脱敏的失败信息；不存在时返回空对象
     */
    @GetMapping("/runs/{runId}")
    public Map<String, Object> runStatus(@PathVariable String runId) {
        return runManager.find(runId, null, null, contextResolver.currentUserId())
                .map(this::runStatus)
                .orElseGet(Map::of);
    }

    /**
     * 请求停止当前用户拥有的聊天运行。
     *
     * @param runId 要停止的聊天运行标识
     * @return 是否已成功提交或确认停止请求
     */
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

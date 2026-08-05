package ai.platform.aiassit.conversation.controller.impl;

import ai.platform.aiassit.conversation.service.ConversationService;
import ai.platform.aiassit.conversation.controller.IConversationController;
import ai.platform.aiassit.conversation.dto.conversation.ConversationDetailResponse;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.conversation.dto.chat.ConversationQueryRequest;
import ai.platform.aiassit.conversation.dto.chat.ConversationQueryResponse;
import ai.platform.aiassit.conversation.dto.chat.ConversationStreamReconnectRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationDetailRequest;
import ai.platform.aiassit.conversation.service.ConversationExecutionService;
import ai.platform.aiassit.conversation.transport.sse.SseConversationTransport;
import ai.platform.aiassit.conversation.support.ConversationCommandFactory;
import ai.platform.aiassit.conversation.support.ConversationRequestContextResolver;
import ai.platform.aiassit.model.service.AiModelConfigService;
import ai.platform.aiassit.service.ai.api.dto.AiEnabledModelDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 兼容旧版聊天协议的会话执行接口。
 *
 * <p>接口会从当前登录上下文补全用户和链路信息，再委托会话服务或执行服务处理；新页面优先使用 {@code /api/chat} 协议接口。</p>
 */
@RestController
@AllArgsConstructor
@Slf4j
public class ConversationController implements IConversationController {

    private final ConversationService conversationService;

    private final ConversationExecutionService executionService;

    private final SseConversationTransport sseTransport;

    private final AiModelConfigService aiModelConfigService;

    private final ConversationCommandFactory commandFactory;

    private final ConversationRequestContextResolver contextResolver;

    /**
     * 查询当前可用于聊天的启用模型。
     *
     * @return 已启用模型的编码、名称和能力摘要
     */
    @Override
    public List<AiEnabledModelDTO> enabledModels() {
        return aiModelConfigService.selectEnabledModels();
    }

    /**
     * 查询当前用户拥有的会话详情。
     *
     * @param request 会话详情请求体，包含会话或轮次定位信息；用户身份由服务端覆盖
     * @return 会话、轮次和消息的聚合详情
     */
    @Override
    public ConversationDetailResponse detail(@RequestBody ConversationDetailRequest request) {
        request.setUserId(contextResolver.currentUserId());
        return conversationService.detailConversation(request);
    }

    /**
     * 同步执行一轮非流式 AI 对话。
     *
     * @param request 对话请求体，包含消息、会话上下文和模型选项
     * @return 对话执行完成后的聚合响应
     */
    @Override
    public ConversationQueryResponse completions(@RequestBody ConversationQueryRequest request) {
        return executionService.execute(buildCommand(request));
    }

    /**
     * 启动一轮流式 AI 对话。
     *
     * @param request 对话请求体，包含消息、会话上下文和模型选项
     * @return SSE 事件流，用于持续接收回答和执行状态
     */
    @Override
    public SseEmitter completionsStream(@RequestBody ConversationQueryRequest request) {
        ConversationQueryCommand command = buildCommand(request);
        log.info("接收到对话流式请求，command={}", command);
        return sseTransport.start(command);
    }

    /**
     * 重新连接已有对话运行的流式输出。
     *
     * @param request 重连请求体，包含运行、会话或轮次定位信息和最后事件游标
     * @return SSE 事件流，从可恢复的事件位置继续推送
     */
    @Override
    public SseEmitter reconnectStream(@RequestBody ConversationStreamReconnectRequest request) {
        return sseTransport.reconnect(request, contextResolver.currentUserId(), contextResolver.traceId());
    }

    private ConversationQueryCommand buildCommand(ConversationQueryRequest request) {
        return commandFactory.fromLegacy(request, contextResolver.currentUserId(), contextResolver.traceId());
    }

}

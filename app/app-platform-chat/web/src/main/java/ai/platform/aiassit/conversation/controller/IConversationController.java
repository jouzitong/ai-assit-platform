package ai.platform.aiassit.conversation.controller;

import ai.platform.aiassit.conversation.dto.conversation.ConversationDetailResponse;
import ai.platform.aiassit.conversation.dto.chat.ConversationQueryRequest;
import ai.platform.aiassit.conversation.dto.chat.ConversationQueryResponse;
import ai.platform.aiassit.conversation.dto.chat.ConversationStreamReconnectRequest;
import ai.platform.aiassit.conversation.dto.conversation.ConversationDetailRequest;
import ai.platform.aiassit.service.ai.api.dto.AiEnabledModelDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 兼容旧版前端的 AI 聊天协议契约。
 *
 * <p>涵盖可用模型查询、会话详情、同步/流式对话和事件流重连；实际用户身份由服务端请求上下文确定。</p>
 */
@RequestMapping("/api/v1/chat")
public interface IConversationController {

    /**
     * 查询当前启用的模型列表。
     *
     * @return 启用模型列表，包含模型编码、展示名称和能力摘要
     */
    @GetMapping("/models/enable")
    List<AiEnabledModelDTO> enabledModels();

    /**
     * 查询会话详情。
     *
     * @param request 会话详情请求体，包含会话或轮次定位信息
     * @return 会话详情，包含当前用户可访问的会话、消息和轮次内容
     */
    @PostMapping("/detail")
    ConversationDetailResponse detail(@RequestBody ConversationDetailRequest request);

    /**
     * 执行非流式 AI 对话。
     *
     * @param request AI 对话请求体，包含消息、会话上下文和模型选项
     * @return 同步执行完成后的对话结果
     */
    @PostMapping("/completions")
    ConversationQueryResponse completions(@RequestBody ConversationQueryRequest request);

    /**
     * 执行流式 AI 对话，并通过 SSE 持续推送结果。
     *
     * @param request AI 对话请求体，包含消息、会话上下文和模型选项
     * @return SSE 事件流，持续推送回答与执行状态
     */
    @PostMapping(value = "/completions/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter completionsStream(@RequestBody ConversationQueryRequest request);

    /**
     * 重新挂接指定会话轮次的流式输出。
     *
     * @param request 流重连请求体，包含运行定位信息和最后事件游标
     * @return 从可恢复位置继续推送的 SSE 事件流
     */
    @PostMapping(value = "/stream/reconnect", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter reconnectStream(@RequestBody ConversationStreamReconnectRequest request);
}

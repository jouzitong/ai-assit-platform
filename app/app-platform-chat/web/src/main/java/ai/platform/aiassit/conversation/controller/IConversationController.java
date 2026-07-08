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
 * AI 聊天接口。
 */
@RequestMapping("/api/v1/chat")
public interface IConversationController {

    /**
     * 查询当前启用的模型列表。
     *
     * @return 启用模型列表
     */
    @GetMapping("/models/enable")
    List<AiEnabledModelDTO> enabledModels();

    /**
     * 查询会话详情。
     *
     * @param request 会话详情查询参数
     * @return 会话详情
     */
    @PostMapping("/detail")
    ConversationDetailResponse detail(@RequestBody ConversationDetailRequest request);

    /**
     * 执行非流式 AI 对话。
     *
     * @param request AI 对话请求参数
     * @return AI 对话结果
     */
    @PostMapping("/completions")
    ConversationQueryResponse completions(@RequestBody ConversationQueryRequest request);

    /**
     * 执行流式 AI 对话，并通过 SSE 持续推送结果。
     *
     * @param request AI 对话请求参数
     * @return SSE 事件流
     */
    @PostMapping(value = "/completions/stream", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter completionsStream(@RequestBody ConversationQueryRequest request);

    /**
     * 重新挂接指定会话轮次的流式输出。
     *
     * @param request 流重连请求参数
     * @return SSE 事件流
     */
    @PostMapping(value = "/stream/reconnect", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter reconnectStream(@RequestBody ConversationStreamReconnectRequest request);
}

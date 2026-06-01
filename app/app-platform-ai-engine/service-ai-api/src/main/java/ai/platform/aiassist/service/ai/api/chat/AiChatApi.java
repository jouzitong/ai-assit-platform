package ai.platform.aiassist.service.ai.api.chat;

import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.stream.ChatStreamObserver;

/**
 * AI 对话能力 API。
 *
 * <p>负责文本对话相关能力，包括同步问答与流式问答。</p>
 */
public interface AiChatApi {

    /**
     * 同步对话（非流式），一次性返回完整结果。
     *
     * @param request 对话请求
     * @return 对话响应
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 流式对话，按增量分片回调。
     *
     * @param request 对话请求
     * @param observer 流式回调观察者
     */
    void chatStream(ChatRequest request, ChatStreamObserver observer);
}

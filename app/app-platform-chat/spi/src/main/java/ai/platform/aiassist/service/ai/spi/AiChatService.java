package ai.platform.aiassist.service.ai.spi;

import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.enums.ProviderType;
import ai.platform.aiassist.service.ai.api.stream.ChatStreamObserver;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderChatRequest;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/21
 */
public interface AiChatService {

    /**
     * 获取当前 AI 提供方类型。
     *
     * @return AI 提供方类型
     */
    ProviderType providerType();

    /**
     * 执行普通对话请求。
     *
     * @param request 提供方对话请求参数
     * @return 对话响应结果
     */
    ChatResponse chat(ProviderChatRequest request);

    /**
     * 执行流式对话请求。
     *
     * @param request  提供方对话请求参数
     * @param observer 流式响应观察器，用于接收模型增量输出、完成事件或异常事件
     */
    void chatStream(ProviderChatRequest request, ChatStreamObserver observer);

}

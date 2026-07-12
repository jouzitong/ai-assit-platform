package ai.platform.aiassit.service.ai.spi;

import ai.platform.aiassit.service.ai.api.dto.ChatResponse;
import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import ai.platform.aiassit.service.ai.api.stream.ChatStreamObserver;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderChatRequest;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderModel;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderModelListRequest;

import java.util.List;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/21
 */
public interface AiChatService {

    /**
     * 获取当前对话客户端类型。
     *
     * @return 对话客户端类型
     */
    AiChatClientType chatClientType();

    /**
     * 查询当前凭证可用的模型列表。
     *
     * <p>对于 OpenAI 兼容服务，该操作对应标准的 {@code GET /v1/models} 接口。
     * 请求中的地址、密钥和超时配置优先于提供方默认配置。</p>
     *
     * @param request 提供方模型列表查询参数
     * @return 提供方返回的模型列表
     */
    List<ProviderModel> listModels(ProviderModelListRequest request);

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

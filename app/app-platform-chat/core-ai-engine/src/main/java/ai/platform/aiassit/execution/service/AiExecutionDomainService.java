package ai.platform.aiassit.execution.service;

import ai.platform.aiassit.service.ai.api.dto.ChatRequest;
import ai.platform.aiassit.service.ai.api.dto.ChatResponse;
import ai.platform.aiassit.service.ai.api.stream.ChatStreamObserver;

/**
 * AI 执行领域服务。
 */
public interface AiExecutionDomainService {

    /**
     * 执行普通对话请求。
     *
     * @param request 对话请求参数，包含模型、消息、上下文等信息
     * @return 对话响应结果
     */
    ChatResponse chat(ChatRequest request);

    /**
     * 执行流式对话请求。
     * <p>
     * 该方法通常用于需要边生成边返回的场景，模型输出会通过观察者逐步回调给调用方。
     *
     * @param request 对话请求参数，包含模型、消息、上下文等信息
     * @param observer 流式响应观察者，用于接收模型输出、异常和完成事件
     */
    void chatStream(ChatRequest request, ChatStreamObserver observer);

    /**
     * 异步执行流式对话请求。
     * <p>
     * 该方法会异步触发流式对话流程，适用于调用方不希望阻塞当前线程的场景。
     *
     * @param request 对话请求参数，包含模型、消息、上下文等信息
     * @param observer 流式响应观察者，用于接收模型输出、异常和完成事件
     */
    void chatStreamAsync(ChatRequest request, ChatStreamObserver observer);

}

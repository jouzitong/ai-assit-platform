package ai.platform.aiassist.service.ai.core.service;

import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassist.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassist.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassist.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertResponse;
import ai.platform.aiassist.service.ai.api.dto.RerankRequest;
import ai.platform.aiassist.service.ai.api.dto.RerankResponse;
import ai.platform.aiassist.service.ai.api.stream.ChatStreamObserver;

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

    /**
     * 执行文本向量化请求。
     * <p>
     * 将输入文本转换为向量表示，通常用于知识库检索、语义匹配等场景。
     *
     * @param request 向量化请求参数，包含待向量化的文本和模型配置等信息
     * @return 向量化响应结果
     */
    EmbedResponse embed(EmbedRequest request);

    /**
     * 执行重排序请求。
     * <p>
     * 根据查询内容对候选文本进行相关性重排，通常用于提升知识库检索结果的准确性。
     *
     * @param request 重排序请求参数，包含查询文本和候选文档列表等信息
     * @return 重排序响应结果
     */
    RerankResponse rerank(RerankRequest request);

    /**
     * 新增或更新知识库文档。
     * <p>
     * 将文档内容写入知识库，通常包含分段、向量化、索引更新等处理流程。
     *
     * @param request 知识库新增或更新请求参数
     * @return 知识库新增或更新响应结果
     */
    KbUpsertResponse kbUpsert(KbUpsertRequest request);

    /**
     * 删除知识库文档。
     * <p>
     * 根据请求中的知识库标识、文档标识或分段标识删除对应数据。
     *
     * @param request 知识库删除请求参数
     * @return 知识库删除响应结果
     */
    KbDeleteResponse kbDelete(KbDeleteRequest request);

    /**
     * 搜索知识库内容。
     * <p>
     * 根据查询文本从知识库中检索相关内容，可结合向量检索、关键词检索和重排序等能力。
     *
     * @param request 知识库搜索请求参数，包含查询文本、知识库范围和检索配置等信息
     * @return 知识库搜索响应结果
     */
    KbSearchResponse kbSearch(KbSearchRequest request);
}

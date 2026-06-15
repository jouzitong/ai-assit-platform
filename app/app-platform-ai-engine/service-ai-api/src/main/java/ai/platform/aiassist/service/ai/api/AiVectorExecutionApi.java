package ai.platform.aiassist.service.ai.api;

import ai.platform.aiassist.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassist.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassist.service.ai.api.dto.RerankRequest;
import ai.platform.aiassist.service.ai.api.dto.RerankResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * AI 向量执行 API（HTTP/Feign）。
 *
 * <p>该接口用于通过 Feign 调用 AI 引擎服务中的向量相关能力，
 * 主要包括文本向量化（Embedding）和文本重排（Rerank）等功能。</p>
 *
 * <p>当前接口通常用于知识库检索、语义召回、候选结果重排等 AI 问答场景。</p>
 */
@FeignClient(
        name = "app-platform-ai-engine",
        contextId = "platformAiEngineClient",
        path = "/aiEngine"
)
public interface AiVectorExecutionApi {

    /**
     * 执行文本向量化。
     *
     * <p>将请求中的文本内容转换为向量表示，便于后续进行向量检索、相似度计算、语义匹配等操作。</p>
     *
     * @param request 向量化请求参数，包含待向量化的文本、模型配置等信息
     * @return 向量化响应结果，包含文本对应的向量数据及相关执行信息
     */
    @PostMapping("/api/v1/ai/execution/vector/embed")
    EmbedResponse embed(@RequestBody EmbedRequest request);

    /**
     * 执行文本重排。
     *
     * <p>根据用户问题与候选文本之间的相关性，对候选结果进行重新排序，
     * 用于提升知识库召回结果、搜索结果或上下文片段的排序质量。</p>
     *
     * @param request 重排请求参数，包含查询文本、候选文本列表、模型配置等信息
     * @return 重排响应结果，包含排序后的候选结果及相关评分信息
     */
    @PostMapping("/api/v1/ai/execution/vector/rerank")
    RerankResponse rerank(@RequestBody RerankRequest request);
}

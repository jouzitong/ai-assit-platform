package ai.platform.aiassist.service.ai.api.vector;

import ai.platform.aiassist.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassist.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassist.service.ai.api.dto.RerankRequest;
import ai.platform.aiassist.service.ai.api.dto.RerankResponse;

/**
 * AI 向量与重排能力 API。
 *
 * <p>负责 embedding 与 rerank 等检索增强能力。</p>
 */
public interface AiVectorApi {

    /**
     * 文本向量化（Embedding）。
     *
     * @param request 向量化请求
     * @return 向量化结果
     */
    EmbedResponse embed(EmbedRequest request);

    /**
     * 重排（Rerank）。
     *
     * @param request 重排请求
     * @return 重排结果
     */
    RerankResponse rerank(RerankRequest request);
}

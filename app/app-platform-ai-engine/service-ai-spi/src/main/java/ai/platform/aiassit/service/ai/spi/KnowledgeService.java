package ai.platform.aiassit.service.ai.spi;

import ai.platform.aiassit.service.ai.api.dto.EmbedResponse;
import ai.platform.aiassit.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassit.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassit.service.ai.api.dto.KbUpsertResponse;
import ai.platform.aiassit.service.ai.api.dto.RerankResponse;
import ai.platform.aiassit.service.ai.api.enums.ProviderType;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderEmbedRequest;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderKbDeleteRequest;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderKbSearchRequest;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderKbUpsertRequest;
import ai.platform.aiassit.service.ai.spi.provider.dto.ProviderRerankRequest;

/**
 * 知识库 服务类
 *
 * @author zhouzhitong
 * @since 2026/6/21
 */
public interface KnowledgeService {

    /**
     * 获取当前 AI 提供方类型。
     *
     * @return AI 提供方类型
     */
    ProviderType providerType();

    /**
     * 执行文本向量化请求。
     *
     * @param request 提供方向量化请求参数
     * @return 向量化响应结果
     */
    EmbedResponse embed(ProviderEmbedRequest request);

    /**
     * 执行重排序请求。
     *
     * @param request 提供方重排序请求参数
     * @return 重排序响应结果
     */
    RerankResponse rerank(ProviderRerankRequest request);

    /**
     * 新增或更新知识库文档数据。
     *
     * @param request 提供方知识库写入请求参数
     * @return 知识库写入响应结果
     */
    KbUpsertResponse kbUpsert(ProviderKbUpsertRequest request);

    /**
     * 删除知识库文档数据。
     *
     * @param request 提供方知识库删除请求参数
     * @return 知识库删除响应结果
     */
    KbDeleteResponse kbDelete(ProviderKbDeleteRequest request);

    /**
     * 执行知识库检索请求。
     *
     * @param request 提供方知识库检索请求参数
     * @return 知识库检索响应结果
     */
    KbSearchResponse kbSearch(ProviderKbSearchRequest request);

}

package ai.platform.aiassist.service.ai.api.knowledge;

import ai.platform.aiassist.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteResponse;
import ai.platform.aiassist.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.KbSearchResponse;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertResponse;

/**
 * AI 知识库能力 API。
 *
 * <p>负责知识库文档写入、删除和检索。</p>
 */
public interface AiKnowledgeBaseApi {

    /**
     * 知识库写入或更新（Upsert）。
     *
     * @param request 写入请求
     * @return 写入结果
     */
    KbUpsertResponse kbUpsert(KbUpsertRequest request);

    /**
     * 知识库删除。
     *
     * @param request 删除请求
     * @return 删除结果
     */
    KbDeleteResponse kbDelete(KbDeleteRequest request);

    /**
     * 知识库检索。
     *
     * @param request 检索请求
     * @return 检索结果
     */
    KbSearchResponse kbSearch(KbSearchRequest request);
}

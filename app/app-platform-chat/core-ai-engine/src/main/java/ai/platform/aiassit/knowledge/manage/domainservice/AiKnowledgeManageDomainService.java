package ai.platform.aiassit.knowledge.manage.domainservice;

import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentDetailDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentContentUpdateRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassit.service.ai.api.dto.AiKbCreateRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbInfoDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbListRequest;
import ai.platform.aiassit.knowledge.manage.req.AiKbDeleteRequest;
import ai.platform.aiassit.knowledge.manage.req.AiKbSyncCheckRequest;
import ai.platform.aiassit.knowledge.manage.resp.AiKbDeleteResponse;
import ai.platform.aiassit.knowledge.manage.resp.AiKbSyncCheckResponse;
import ai.platform.aiassit.knowledge.manage.req.AiKbSyncRequest;
import ai.platform.aiassit.knowledge.manage.resp.AiKbSyncResponse;
import org.athena.framework.data.jdbc.vo.PageResultVO;

import java.util.List;

/**
 * AI 知识库领域服务。
 *
 * <p>负责封装知识库、知识库文档、文档同步等核心业务能力，
 * 对上层控制器屏蔽具体的知识库提供方、文档版本、同步状态等实现细节。</p>
 */
public interface AiKnowledgeManageDomainService {

    /**
     * 查询知识库列表。
     *
     * @param request 知识库列表查询请求，包含知识库编码、名称、业务类型等筛选条件
     * @return 知识库信息列表
     */
    List<AiKbInfoDTO> kbList(AiKbListRequest request);

    /**
     * 创建知识库主记录。
     *
     * @param request 知识库创建请求
     * @return 创建后的知识库信息
     */
    AiKbInfoDTO createKnowledgeBase(AiKbCreateRequest request);

    /**
     * 获取知识库提供方侧的知识库 ID。
     *
     * <p>通常根据平台内部的知识库编码或查询条件，解析出第三方知识库平台实际使用的知识库 ID。</p>
     *
     * @param request 知识库查询请求
     * @return 知识库提供方侧的知识库 ID
     */
    String getKbId(AiKbListRequest request);

    /**
     * 查询知识库文档列表。
     *
     * @param request 知识库文档列表查询请求，包含知识库编码、文档类型、业务类型等筛选条件
     * @return 知识库文档列表项
     */
    PageResultVO<AiKbDocumentListItemDTO> listDocuments(AiKbDocumentListRequest request);

    /**
     * 获取知识库文档详情。
     *
     * @param kbCode 知识库编码
     * @param documentCode 文档编码
     * @return 知识库文档详情
     */
    AiKbDocumentDetailDTO getDocumentDetail(String kbCode, String documentCode);

    /**
     * 新增或更新知识库文档。
     *
     * <p>当文档不存在时创建文档；当文档已存在时更新文档基础信息或内容，
     * 并根据业务需要维护文档版本信息。</p>
     *
     * @param request 知识库文档新增或更新请求
     * @return 文档新增或更新结果
     */
    AiKbDocumentUpsertResponse upsertDocument(AiKbDocumentUpsertRequest request);

    /**
     * 根据本地文档主键 ID 更新文档正文。
     *
     * <p>正文变更后会维护文档历史快照，并将提供方同步状态重置为待同步。</p>
     *
     * @param request 文档正文更新请求
     * @return 文档更新结果
     */
    AiKbDocumentUpsertResponse updateDocumentContent(AiKbDocumentContentUpdateRequest request);

    /**
     * 同步知识库文档到知识库提供方。
     *
     * <p>用于将平台侧维护的文档内容同步到外部知识库平台，并记录同步结果、提供方文档 ID、同步状态等信息。</p>
     *
     * @param request 文档同步请求
     * @return 文档同步结果
     */
    AiKbSyncResponse syncDocument(AiKbSyncRequest request);

    AiKbSyncCheckResponse checkDocumentSync(AiKbSyncCheckRequest request);

    AiKbDeleteResponse deleteDocument(AiKbDeleteRequest request);
}

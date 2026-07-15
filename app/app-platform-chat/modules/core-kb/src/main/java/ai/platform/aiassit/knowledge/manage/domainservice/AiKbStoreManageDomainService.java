package ai.platform.aiassit.knowledge.manage.domainservice;

import ai.platform.aiassit.knowledge.manage.entity.store.dto.AiKbStoreDTO;
import ai.platform.aiassit.knowledge.manage.entity.store.req.AiKbStoreQueryRequest;
import ai.platform.aiassit.knowledge.manage.vo.AiKbStoreVO;
import org.athena.framework.data.jdbc.vo.PageResultVO;

/** 知识库配置管理编排：认证凭据持久化、脱敏输出与 CRUD 更新合并。 */
public interface AiKbStoreManageDomainService {

    PageResultVO<AiKbStoreVO> page(AiKbStoreQueryRequest request);

    AiKbStoreVO get(Long id);

    AiKbStoreVO add(AiKbStoreDTO dto);

    AiKbStoreVO update(Long id, AiKbStoreDTO dto);

    AiKbStoreVO edit(Long id, AiKbStoreDTO dto);

    boolean delete(Long id);

    boolean retrySync(Long id);
}

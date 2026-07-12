package ai.platform.aiassit.service.ai.api.dto;

import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** 删除知识库 Provider Dataset 的请求。 */
@Data
public class AiKbDatasetDeleteRequest implements Serializable {

    /** 知识库提供方类型；未传时默认 RAGFlow。 */
    private AiKnowledgeClientType clientType = AiKnowledgeClientType.RAGFLOW;

    /** 要删除的 Provider Dataset ID 列表。 */
    private List<String> kbIds = new ArrayList<>();

    /** 是否删除当前 Provider 账号下的全部 Dataset。该操作不可恢复。 */
    private Boolean deleteAll = Boolean.FALSE;

    /** Provider 调用上下文；认证信息仅由服务端注入。 */
    private RequestMeta meta = new RequestMeta();
}

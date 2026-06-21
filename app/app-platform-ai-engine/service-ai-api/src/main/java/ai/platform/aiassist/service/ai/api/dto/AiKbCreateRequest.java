package ai.platform.aiassist.service.ai.api.dto;

import ai.platform.aiassist.service.ai.api.enums.AiKbSourceType;
import ai.platform.aiassist.service.ai.api.enums.AiKbStoreStatus;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

@Data
public class AiKbCreateRequest implements Serializable {

    /** 本地知识库编码。 */
    private String kbCode;

    /** 知识库名称。 */
    private String kbName;

    /** 来源对象类型。 */
    private AiKbSourceType sourceType;

    /** 来源对象唯一键。 */
    private String sourceKey;

    /** AI 侧知识库 ID。 */
    private String providerKbId;

    /** 知识库状态。 */
    private AiKbStoreStatus status;

    /** 扩展参数。 */
    private Map<String, Object> ext;
}

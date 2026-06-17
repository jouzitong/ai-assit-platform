package ai.platform.aiassist.service.ai.api.dto;

import ai.platform.aiassist.service.ai.api.enums.AiKbSourceType;
import ai.platform.aiassist.service.ai.api.enums.AiKbStoreStatus;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AiKbInfoDTO implements Serializable {

    /** 本地知识库标识。 */
    private String kbId;

    /** 知识库名称。 */
    private String kbName;

    /** 来源对象类型。 */
    private AiKbSourceType sourceType;

    /** 来源对象唯一键。 */
    private String sourceKey;

    /** AI 侧知识库标识。 */
    private String providerKbId;

    /** 知识库状态。 */
    private AiKbStoreStatus status;

    /** 是否启用。 */
    private Boolean enabled;

    /** 扩展参数。 */
    private Map<String, Object> ext = new LinkedHashMap<>();
}

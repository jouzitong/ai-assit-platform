package ai.platform.aiassist.service.ai.api.dto;

import ai.platform.aiassist.service.ai.api.enums.AiKbSourceType;
import lombok.Data;

import java.io.Serializable;

@Data
public class AiKbListRequest implements Serializable {

    /** 来源对象类型，可选。 */
    private AiKbSourceType sourceType;

    /** 来源对象唯一键，可选。 */
    private String sourceKey;

    /** 是否仅返回启用项。 */
    private Boolean enabled;
}

package ai.platform.aiassist.service.ai.api.dto;

import ai.platform.aiassist.service.ai.api.enums.AiKbBizType;
import lombok.Data;

import java.io.Serializable;

@Data
public class AiKbListRequest implements Serializable {

    /** 业务类型，可选。 */
    private AiKbBizType bizType;

    /** 来源对象唯一键，可选。 */
    private String sourceKey;

    /** 是否仅返回启用项。 */
    private Boolean enabled;
}

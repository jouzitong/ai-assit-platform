package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiKbListRequest implements Serializable {

    /** 是否仅返回启用项。 */
    private Boolean enabled;
}

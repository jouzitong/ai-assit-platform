package ai.platform.aiassit.user.api.dto;

import lombok.Data;

@Data
public class ErrCodeQueryRequest {

    private Integer code;

    private String locale;
}

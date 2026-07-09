package ai.platform.aiassit.user.api.dto;

import lombok.Data;

@Data
public class ErrCodeQueryResponse {

    private Integer code;

    private Integer httpStatus;

    private String locale;

    private String messageTemplate;

    private String description;
}

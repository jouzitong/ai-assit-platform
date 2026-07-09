package ai.platform.aiassit.user.errcode.data.entity.dto;

import lombok.Data;

@Data
public class ErrCodeResolveDTO {

    private Integer code;

    private Integer httpStatus;

    private String locale;

    private String messageTemplate;

    private String description;
}

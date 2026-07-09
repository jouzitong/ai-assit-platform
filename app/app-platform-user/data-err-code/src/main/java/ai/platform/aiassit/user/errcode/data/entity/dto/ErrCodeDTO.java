package ai.platform.aiassit.user.errcode.data.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class ErrCodeDTO extends BaseDTO {

    private Integer code;

    private Integer httpStatus;

    private String description;

    private String tags;
}

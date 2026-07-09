package ai.platform.aiassit.user.errcode.data.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class ErrCodeI18nDTO extends BaseDTO {

    private Integer errCode;

    private String locale;

    private String messageTemplate;

    private String description;
}

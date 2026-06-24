package ai.platform.aiassit.render.data.render.entity.dto;

import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class RenderPageDTO extends AuditableDTO {

    private String code;

    private String name;

    private String categoryCode;

    private EffectiveStatus status;
}

package ai.platform.aiassit.render.data.render.entity.vo;

import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class RenderPageManageVO extends AuditableDTO {

    private String code;

    private String name;

    private String categoryCode;

    private EffectiveStatus status;

    private String content;
}

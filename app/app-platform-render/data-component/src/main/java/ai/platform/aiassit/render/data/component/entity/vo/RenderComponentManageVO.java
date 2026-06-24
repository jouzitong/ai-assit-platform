package ai.platform.aiassit.render.data.component.entity.vo;

import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class RenderComponentManageVO extends AuditableDTO {

    private String key;

    private String name;

    private String category;

    private EffectiveStatus status;

    private String docMarkdown;

    private String exampleJson;
}

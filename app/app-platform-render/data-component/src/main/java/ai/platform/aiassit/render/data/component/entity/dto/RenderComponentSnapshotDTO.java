package ai.platform.aiassit.render.data.component.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class RenderComponentSnapshotDTO extends AuditableDTO {

    private String componentKey;

    private String docMarkdown;

    private String exampleJson;
}

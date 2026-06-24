package ai.platform.aiassit.render.data.render.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class RenderPageSnapshotDTO extends AuditableDTO {

    private String pageCode;

    private String content;
}

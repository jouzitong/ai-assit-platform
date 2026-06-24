package ai.platform.aiassit.render.data.render.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class RenderPageCategoryDTO extends AuditableDTO {

    private String code;

    private String name;

    private String parentCode;

    private String path;

    private Integer sortNo;

    private Boolean enabled;
}

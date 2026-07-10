package ai.platform.aiassit.render.data.render.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class RenderPageContentDTO extends AuditableDTO {

    private String pageCode;

    private Map<String, Object> content;
}

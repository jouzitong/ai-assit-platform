package ai.platform.aiassit.knowledge.manage.entity.dto;

import ai.platform.aiassit.service.ai.api.enums.AiKbContentFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbDocumentVersionContentDTO extends BaseDTO {

    private Long documentVersionId;

    private AiKbContentFormat contentFormat;

    private Long contentSize;

    private Map<String, Object> contentJson;

    private String renderedContent;

    private Map<String, Object> extJson;
}

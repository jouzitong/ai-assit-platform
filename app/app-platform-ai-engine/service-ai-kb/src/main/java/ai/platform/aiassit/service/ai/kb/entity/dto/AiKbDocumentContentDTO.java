package ai.platform.aiassit.service.ai.kb.entity.dto;

import ai.platform.aiassit.service.ai.api.enums.AiKbContentFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbDocumentContentDTO extends BaseDTO {

    private Long documentId;

    private AiKbContentFormat contentFormat;

    private Long contentSize;

    private Map<String, Object> contentJson;

    private String renderedContent;

    private Map<String, Object> extJson;
}

package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbDocumentDetailDTO extends AiKbDocumentListItemDTO {

    private String contentChecksum;

    private Map<String, Object> metaJson;

    private String lastError;

    private String remark;

    private Map<String, Object> contentJson;

    private String renderedContent;

    private Map<String, Object> extJson;
}

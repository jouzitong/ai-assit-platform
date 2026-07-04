package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class AiKbDocumentContentUpdateRequest implements Serializable {

    /**
     * 本地文档主键 ID。
     */
    private Long documentId;

    /**
     * 新的文档正文内容。
     */
    private String content;

    /**
     * 正文扩展参数。
     */
    private Map<String, Object> ext = new LinkedHashMap<>();
}

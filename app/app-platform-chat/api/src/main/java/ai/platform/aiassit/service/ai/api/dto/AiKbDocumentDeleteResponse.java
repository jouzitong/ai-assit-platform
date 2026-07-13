package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiKbDocumentDeleteResponse implements Serializable {

    private Integer deletedCount = 0;

    private List<String> skippedDocumentCodes = new ArrayList<>();
}

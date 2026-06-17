package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiKbDocumentListRequest implements Serializable {

    private String kbCode;

    private String documentCode;
}

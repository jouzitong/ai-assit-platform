package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class AiKbDocumentListRequest implements Serializable {

    private String kbCode;

    private String documentCode;

    private String keyword;

    private Integer bizTypeCode;

    private String tab;

    private Integer page;

    private Integer size;
}

package ai.platform.aiassist.service.ai.kb.controller.resp;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiKbDeleteResponse implements Serializable {

    private Integer deletedCount = 0;

    private Integer deletedContentCount = 0;

    private Integer deletedVersionCount = 0;

    private Integer deletedVersionContentCount = 0;

    private List<String> skippedDocumentCodes = new ArrayList<>();
}

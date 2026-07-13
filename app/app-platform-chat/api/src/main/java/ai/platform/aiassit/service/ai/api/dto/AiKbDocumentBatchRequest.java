package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiKbDocumentBatchRequest implements Serializable {

    /** 可选知识库编码；为空时在全部知识库中查询或删除。 */
    private String kbCode;

    /** 文档稳定编码列表。 */
    private List<String> documentCodes = new ArrayList<>();
}

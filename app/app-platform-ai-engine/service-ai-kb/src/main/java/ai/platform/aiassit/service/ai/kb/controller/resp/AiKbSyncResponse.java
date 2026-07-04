package ai.platform.aiassit.service.ai.kb.controller.resp;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
public class AiKbSyncResponse implements Serializable {

    /**
     * 本次受理同步的文档数。
     */
    private Integer acceptedCount;

    /**
     * 已创建的同步任务编号，可选。
     */
    private String taskCode;

    /**
     * 未受理的文档编码列表，可选。
     */
    private List<String> skippedDocumentCodes = new ArrayList<>();
}

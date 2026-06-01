package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;



@Data
public class KbUpsertResponse implements Serializable {

    /** 知识库唯一标识 */
    private String kbId;
    /** 成功写入/更新的文档数 */
    private Integer accepted = 0;
    /** 失败文档数 */
    private Integer failed = 0;
    /** 失败文档 ID 列表 */
    private List<String> failedDocumentIds = new ArrayList<>();
}

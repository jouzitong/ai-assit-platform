package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;



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
    /** 本地文档 ID 到知识库提供方文档 ID 的映射 */
    private Map<String, String> documentIdMappings = new LinkedHashMap<>();
}

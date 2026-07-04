package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;



@Data
public class KbSearchItem implements Serializable {

    /** 文档唯一标识 */
    private String documentId;
    /** 检索相关性分数 */
    private Double score;
    /** 命中文本内容 */
    private String content;
    /** 命中文档元数据 */
    private Map<String, Object> metadata = new HashMap<>();
}

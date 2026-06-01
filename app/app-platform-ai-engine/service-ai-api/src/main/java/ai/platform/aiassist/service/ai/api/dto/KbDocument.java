package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;



@Data
public class KbDocument implements Serializable {

    /** 文档唯一标识 */
    private String documentId;
    /** 文档正文内容 */
    private String content;
    /** 文档来源（URL、文件路径、业务来源等） */
    private String source;
    /** 自定义元数据（标签、作者、时间等） */
    private Map<String, Object> metadata = new HashMap<>();
}

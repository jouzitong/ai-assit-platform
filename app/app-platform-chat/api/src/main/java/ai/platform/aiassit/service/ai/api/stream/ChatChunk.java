package ai.platform.aiassit.service.ai.api.stream;

import ai.platform.aiassit.service.ai.api.enums.OutputType;
import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 流式对话分片数据。
 */
@Data
public class ChatChunk implements Serializable {

    /** 请求唯一标识，用于串联同一次流式会话 */
    private String requestId;
    /** 当前分片的输出类型 */
    private OutputType outputType;
    /** 当前分片增量文本（delta） */
    private String delta;
    /** 内部流事件类型，例如 answer_delta、progress */
    private String eventType;
    /** progress 事件的子类型，例如 ACTIVITY */
    private String progressType;
    /** 事件来源 */
    private String source;
    /** 事件执行阶段 */
    private String phase;
    /** 事件状态 */
    private String status;
    /** 面向用户的活动说明 */
    private String message;
    /** 活动结构化扩展信息 */
    private Map<String, Object> ext = new LinkedHashMap<>();
}

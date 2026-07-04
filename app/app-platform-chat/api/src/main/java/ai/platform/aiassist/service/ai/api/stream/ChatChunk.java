package ai.platform.aiassist.service.ai.api.stream;

import ai.platform.aiassist.service.ai.api.enums.OutputType;
import lombok.Data;

import java.io.Serializable;

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
}

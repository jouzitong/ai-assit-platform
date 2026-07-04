package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import ai.platform.aiassist.service.ai.api.enums.FinishReason;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



@Data
public class ChatResponse implements Serializable {

    /** 请求唯一标识 */
    private String requestId;
    /** 实际执行模型名称 */
    private String model;
    /** 输出项列表（文本/工具调用/JSON） */
    private List<OutputItem> outputs = new ArrayList<>();
    /** token 用量信息 */
    private Usage usage = new Usage();
    /** 结束原因 */
    private FinishReason finishReason;
    /** 平台原始扩展信息（调试与审计使用） */
    private Map<String, Object> providerMeta = new HashMap<>();
}

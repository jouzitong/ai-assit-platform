package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;



@Data
public class EmbedResponse implements Serializable {

    /** 请求唯一标识 */
    private String requestId;
    /** 实际执行模型 */
    private String model;
    /** 向量结果列表 */
    private List<EmbeddingItem> vectors = new ArrayList<>();
    /** token 用量信息 */
    private Usage usage = new Usage();
}

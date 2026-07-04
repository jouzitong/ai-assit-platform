package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;



@Data
public class EmbeddingItem implements Serializable {

    /** 输入文本在请求列表中的序号 */
    private Integer index;
    /** 向量数据 */
    private List<Double> vector = new ArrayList<>();
}

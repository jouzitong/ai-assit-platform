package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;



@Data
public class RerankItem implements Serializable {

    /** 候选文本原始索引 */
    private Integer index;
    /** 相关性分数，越大越相关 */
    private Double score;
    /** 候选文本内容 */
    private String text;
}

package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;



@Data
public class Usage implements Serializable {

    /** 输入 token 数 */
    private Integer inputTokens = 0;
    /** 输出 token 数 */
    private Integer outputTokens = 0;
    /** token 总数 */
    private Integer totalTokens = 0;
}

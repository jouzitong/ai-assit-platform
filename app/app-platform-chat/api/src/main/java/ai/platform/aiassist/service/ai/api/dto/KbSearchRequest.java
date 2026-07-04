package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;



@Data
public class KbSearchRequest implements Serializable {

    /** 知识库唯一标识 */
    private String kbId;
    /** 用户检索语句 */
    private String query;
    /** 返回命中条数上限 */
    private Integer topK = 5;
    /** 请求上下文信息 */
    private RequestMeta meta = new RequestMeta();
}

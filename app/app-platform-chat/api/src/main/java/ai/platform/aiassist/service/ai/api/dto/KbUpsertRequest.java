package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;



@Data
public class KbUpsertRequest implements Serializable {

    /** 知识库唯一标识 */
    private String kbId;
    /** 待写入或更新的文档列表 */
    private List<KbDocument> documents = new ArrayList<>();
    /** 请求上下文信息 */
    private RequestMeta meta = new RequestMeta();
}

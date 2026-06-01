package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;



@Data
public class KbDeleteRequest implements Serializable {

    /** 知识库唯一标识 */
    private String kbId;
    /** 需要删除的文档 ID 列表 */
    private List<String> documentIds = new ArrayList<>();
    /** 请求上下文信息 */
    private RequestMeta meta = new RequestMeta();
}

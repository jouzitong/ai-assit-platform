package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;



@Data
public class RequestMeta implements Serializable {

    /** 链路追踪 ID */
    private String traceId;
    /** 租户标识 */
    private String tenantId;
    /** 业务场景标识 */
    private String scene;
    /** 额外上下文扩展 */
    private Map<String, Object> ext = new HashMap<>();
}

package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class AiKbInfoDTO implements Serializable {

    /** 本地知识库标识。 */
    private String kbId;

    /** 知识库名称。 */
    private String kbName;

    /** AI 侧知识库标识。 */
    private String providerKbId;

    /** 是否启用。 */
    private Boolean enabled;

    /** 标签列表。 */
    private List<String> tags;

    /** 知识库请求地址。 */
    private String url;

    /** 扩展参数。 */
    private Map<String, Object> ext = new LinkedHashMap<>();
}

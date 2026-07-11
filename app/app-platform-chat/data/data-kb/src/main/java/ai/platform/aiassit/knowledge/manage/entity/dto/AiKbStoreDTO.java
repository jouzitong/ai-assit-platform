package ai.platform.aiassit.knowledge.manage.entity.dto;

import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.util.List;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbStoreDTO extends BaseDTO {

    private String kbCode;

    private String kbName;

    private AiKnowledgeClientType clientType;

    private String providerKbId;

    private Boolean enabled;

    private List<String> tags;

    private String url;

    private Map<String, Object> extJson;
}

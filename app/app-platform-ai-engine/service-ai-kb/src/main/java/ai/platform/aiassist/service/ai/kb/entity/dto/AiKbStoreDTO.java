package ai.platform.aiassist.service.ai.kb.entity.dto;

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

    private String providerKbId;

    private Boolean enabled;

    private List<String> tags;

    private String url;

    private Map<String, Object> extJson;
}

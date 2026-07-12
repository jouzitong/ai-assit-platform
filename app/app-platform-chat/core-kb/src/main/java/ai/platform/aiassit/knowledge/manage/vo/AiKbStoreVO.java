package ai.platform.aiassit.knowledge.manage.vo;

import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

import java.util.List;
import java.util.Map;

/** 面向管理页面的本地知识库配置。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbStoreVO extends AuditableDTO {

    private String kbCode;

    private String kbName;

    private AiKnowledgeClientType clientType;

    private String providerKbId;

    private Boolean enabled;

    private List<String> tags;

    private String url;

    private AiKbAuthVO auth;

    private Map<String, Object> extJson;
}

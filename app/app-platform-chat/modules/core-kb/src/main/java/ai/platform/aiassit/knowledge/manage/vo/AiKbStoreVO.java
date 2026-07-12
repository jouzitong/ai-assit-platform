package ai.platform.aiassit.knowledge.manage.vo;

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

    private String providerKbId;

    private String description;

    private String embeddingModel;

    private String permission;

    private String chunkMethod;

    private Map<String, Object> parserConfig;

    private String parseType;

    private String pipelineId;

    private Boolean enabled;

    private List<String> tags;

    /** 脱敏后的认证配置。 */
    private AiKbAuthVO auth;

    private Map<String, Object> extJson;
}

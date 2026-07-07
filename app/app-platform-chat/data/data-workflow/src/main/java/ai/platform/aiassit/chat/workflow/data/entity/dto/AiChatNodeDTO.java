package ai.platform.aiassit.chat.workflow.data.entity.dto;

import ai.platform.aiassit.chat.workflow.data.entity.config.WorkflowNodeCatalogConfig;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatNodeDTO extends BaseDTO {

    private String code;

    private String name;

    private String type;

    private Boolean enabled = Boolean.TRUE;

    private WorkflowNodeCatalogConfig config;
}

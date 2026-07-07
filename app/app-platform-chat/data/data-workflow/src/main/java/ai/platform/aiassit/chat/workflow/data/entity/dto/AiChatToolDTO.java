package ai.platform.aiassit.chat.workflow.data.entity.dto;

import ai.platform.aiassit.chat.workflow.data.enums.AiChatToolSyncStatus;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatToolDTO extends BaseDTO {

    private String code;

    private String name;

    private String desc;

    private String content;

    private String runtimeType;

    private AiChatToolSyncStatus syncStatus;

    private Boolean enabled = Boolean.TRUE;

    private String remark;
}

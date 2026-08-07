package ai.platform.aiassit.conversation.data.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

/** 分组数据传输对象。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationGroupDTO extends AuditableDTO {

    private String groupCode;

    private Long userId;

    private String groupName;
}

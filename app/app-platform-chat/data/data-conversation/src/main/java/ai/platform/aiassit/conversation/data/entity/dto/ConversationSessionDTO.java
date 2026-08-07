package ai.platform.aiassit.conversation.data.entity.dto;

import ai.platform.aiassit.conversation.data.enums.ConversationBusinessType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationSessionDTO extends AuditableDTO {

    private String sessionCode;

    private Long userId;

    private String groupCode;

    private ConversationBusinessType businessType;

    private String sessionName;

    private Boolean pinned = Boolean.FALSE;
}

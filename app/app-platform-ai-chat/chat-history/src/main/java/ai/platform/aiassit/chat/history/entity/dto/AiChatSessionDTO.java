package ai.platform.aiassit.chat.history.entity.dto;

import ai.platform.aiassit.chat.history.entity.enums.AiChatBusinessType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatSessionDTO extends BaseDTO {

    private String sessionCode;

    private Long userId;

    private AiChatBusinessType businessType;

    private String sessionName;

    private Boolean pinned = Boolean.FALSE;
}

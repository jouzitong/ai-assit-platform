package ai.platform.aiassit.chat.history.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatSessionDTO extends BaseDTO {

    private String sessionCode;

    private Long userId;

    private String sessionName;
}

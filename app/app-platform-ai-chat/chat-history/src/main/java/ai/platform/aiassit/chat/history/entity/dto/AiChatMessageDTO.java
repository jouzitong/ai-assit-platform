package ai.platform.aiassit.chat.history.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatMessageDTO extends BaseDTO {

    private String messageCode;

    private String roundCode;

    private String sessionCode;

    private String role;

    private String content;

    private Integer sortNo;
}

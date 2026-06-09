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

    private String actorType;

    private String messageType;

    private String displayLevel;

    private String contentFormat;

    private String parentMessageCode;

    private String sourceMessageCode;

    private String status;

    private String content;

    private Integer sortNo;

    private String extJson;
}

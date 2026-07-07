package ai.platform.aiassit.chat.history.entity.dto;

import ai.platform.aiassit.chat.history.enums.AiChatRoundType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiChatRoundDTO extends BaseDTO {

    private String roundCode;

    private AiChatRoundType roundType;

    private String parentRoundCode;

    private String sessionCode;

    private Long userId;

    private String modelCode;

    private String actualModel;

    private String status;
}

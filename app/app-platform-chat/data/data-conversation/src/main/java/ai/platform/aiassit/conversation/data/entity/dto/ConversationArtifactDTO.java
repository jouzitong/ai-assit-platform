package ai.platform.aiassit.conversation.data.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationArtifactDTO extends BaseDTO {

    private String artifactCode;

    private String sessionCode;

    private String roundCode;

    private Long userId;

    private String relatedMessageCode;

    private String artifactType;

    private String stage;

    private String producerType;

    private Boolean visibleFlag;

    private String title;

    private String content;

    private String contentFormat;

    private String status;

    private Integer seqNo;

    private String extJson;
}

package ai.platform.aiassit.conversation.data.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationArtifactDTO extends BaseDTO {

    private String artifactCode;

    private String roundCode;

    private String artifactType;

    private String stage;

    private String title;

    private String content;

    private String contentFormat;

    private Integer seqNo;

    private String extJson;
}

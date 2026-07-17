package ai.platform.aiassit.conversation.data.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.AuditableDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationActivityDTO extends AuditableDTO {

    private String activityCode;
    private String sessionCode;
    private String roundCode;
    private Long userId;
    private String agentCode;
    private String correlationCode;
    private String activityType;
    private String activityName;
    private String source;
    private String phase;
    private String status;
    private String message;
    private String inputSummary;
    private String outputSummary;
    private Long durationMs;
    private String requestId;
    private Integer seqNo;
    private String detailJson;
}

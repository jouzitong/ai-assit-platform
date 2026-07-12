package ai.platform.aiassit.knowledge.manage.entity.task.dto;

import ai.platform.aiassit.service.ai.api.enums.AiKbPublishStage;
import ai.platform.aiassit.service.ai.api.enums.AiKbTaskStatus;
import ai.platform.aiassit.service.ai.api.enums.AiKbTaskType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
public class AiKbPublishTaskDTO extends BaseDTO {

    private String taskCode;

    private String kbCode;

    private AiKbTaskType taskType;

    private AiKbTaskStatus status;

    private Integer progressPercent;

    private AiKbPublishStage currentStage;

    private Map<String, Object> requestJson;

    private Map<String, Object> resultJson;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;
}

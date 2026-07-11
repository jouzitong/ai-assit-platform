package ai.platform.aiassit.conversation.dto.task;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ConversationTaskStatusResponse {

    private String runId;

    private String ownerNodeId;

    private String sessionCode;

    private String roundCode;

    private Boolean active = Boolean.FALSE;

    private String status;

    private String requestId;

    private String createdAt;

    private String startedAt;

    private String finishedAt;

    private String error;

    private List<String> taskCodes = new ArrayList<>();
}

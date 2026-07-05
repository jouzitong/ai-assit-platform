package ai.platform.aiassit.conversation.query.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiChatTaskStatusResponse {

    private String sessionCode;

    private String roundCode;

    private Boolean active = Boolean.FALSE;

    private String status;

    private List<String> taskCodes = new ArrayList<>();
}

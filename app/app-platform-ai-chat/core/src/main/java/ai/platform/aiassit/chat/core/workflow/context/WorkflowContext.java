package ai.platform.aiassit.chat.core.workflow.context;

import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
@Data
public class WorkflowContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private AiChatQueryCommand command;

    private String workflowCode;

    private AiChatSessionDTO session;

    private List<AiChatMessageDTO> sessionMessages = new ArrayList<>();

    private AiChatRoundDTO round;

    private ChatRequest engineRequest;

    private ChatResponse engineResponse;

    private String analysisResult;

    private String knowledgeBaseId;

    private String knowledgeResult;

    private String generatedSql;

    private String validatedSql;

    private String sqlValidationError;

    private String sqlExecutionStatus;

    private Object sqlExecutionResult;

    private String renderedAnswer;

    private Map<String, Object> data = new HashMap<>();

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public <T> T get(String key) {
        return (T) data.get(key);
    }


}

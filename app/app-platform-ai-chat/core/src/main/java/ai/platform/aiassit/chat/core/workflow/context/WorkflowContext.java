package ai.platform.aiassit.chat.core.workflow.context;

import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.ChatResponse;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryStreamEvent;
import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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
@Slf4j
public class WorkflowContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String STATUS_RUNNING = "RUNNING";

    private AiChatQueryCommand command;

    private String workflowCode;

    private AiChatSessionDTO session;

    private List<AiChatMessageDTO> sessionMessages = new ArrayList<>();

    private List<AiChatArtifactDTO> sessionArtifacts = new ArrayList<>();

    private AiChatMessageDTO currentUserMessage;

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

    private transient SseEmitter emitter;

    private Map<String, Object> data = new HashMap<>();

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public void publishEvent(String eventType, String message) {
        publishEvent(eventType, message, null, null, STATUS_RUNNING);
    }

    public void publishEvent(String eventType, String message, String answer, String delta, String status) {
        if (emitter == null) {
            return;
        }
        AiChatQueryStreamEvent event = new AiChatQueryStreamEvent();
        event.setEventType(eventType);
        event.setRequestId(command == null ? null : command.getTraceId());
        event.setSessionCode(session == null ? null : session.getSessionCode());
        event.setRoundCode(round == null ? null : round.getRoundCode());
        event.setMessage(message);
        event.setAnswer(answer);
        event.setDelta(delta);
        event.setStatus(status);
        try {
            emitter.send(SseEmitter.event().name(eventType).data(event));
        } catch (IOException ex) {
            log.warn("failed to publish workflow event, eventType={}", eventType, ex);
        }
    }

}

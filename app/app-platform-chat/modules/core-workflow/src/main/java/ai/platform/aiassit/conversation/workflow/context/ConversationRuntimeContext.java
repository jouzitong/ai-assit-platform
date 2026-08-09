package ai.platform.aiassit.conversation.workflow.context;

import ai.platform.aiassit.conversation.data.entity.dto.ConversationArtifactDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationRoundDTO;
import ai.platform.aiassit.conversation.data.entity.dto.ConversationSessionDTO;
import ai.platform.aiassit.conversation.constant.ConversationEventPhases;
import ai.platform.aiassit.conversation.constant.ConversationEventTypes;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.conversation.workflow.runtime.ConversationCancellation;
import ai.platform.aiassit.conversation.workflow.runtime.ConversationEventPublisher;
import lombok.Data;
import org.springframework.util.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runtime state for one Agent-backed conversation round.
 *
 * <p>The class intentionally contains no workflow-node state. Artifact workflows are
 * immutable acceptance contracts consumed after an Agent run, not executable node graphs.</p>
 */
@Data
public class ConversationRuntimeContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final String STATUS_RUNNING = "RUNNING";

    private ConversationQueryCommand command;
    /** Trusted tenant copied from the server-built command for this round. */
    private String tenantId;
    private ConversationSessionDTO session;
    private ConversationRoundDTO round;
    private List<ConversationArtifactDTO> sessionArtifacts = new ArrayList<>();
    private ConversationContextPackage contextPackage = new ConversationContextPackage();
    private UserMessageContext userMessageContext = new UserMessageContext();
    private String renderedAnswer;
    private transient ConversationEventPublisher eventPublisher = ConversationEventPublisher.NOOP;
    private transient ConversationCancellation cancellation = ConversationCancellation.NONE;

    public UserMessageContext getOrCreateUserMessageContext() {
        if (userMessageContext == null) {
            userMessageContext = new UserMessageContext();
        }
        return userMessageContext;
    }

    /** Defensive normalization after the current user message has been persisted. */
    public void refreshUserMessageContext() {
        UserMessageContext messages = getOrCreateUserMessageContext();
        messages.setSessionMessages(messages.getSessionMessages() == null
                ? new ArrayList<>() : new ArrayList<>(messages.getSessionMessages()));
    }

    public void checkCancellation() {
        ConversationCancellation signal = cancellation == null ? ConversationCancellation.NONE : cancellation;
        signal.throwIfCancellationRequested();
    }

    public void publishEvent(String eventType,
                             String source,
                             String phase,
                             String message,
                             String answer,
                             String delta,
                             String status,
                             Map<String, Object> ext) {
        publishEvent(eventType, null, source, phase, message, answer, delta, status, ext);
    }

    public void publishEvent(String eventType,
                             String progressType,
                             String source,
                             String phase,
                             String message,
                             String answer,
                             String delta,
                             String status,
                             Map<String, Object> ext) {
        ConversationQueryStreamEvent event = new ConversationQueryStreamEvent();
        event.setEventType(eventType);
        event.setProgressType(progressType);
        event.setSource(source);
        event.setPhase(phase);
        event.setRequestId(command == null ? null : command.getTraceId());
        event.setSessionCode(session == null ? null : session.getSessionCode());
        event.setSessionName(session == null ? null : session.getSessionName());
        event.setRoundCode(round == null ? null : round.getRoundCode());
        event.setMessage(message);
        event.setAnswer(answer);
        event.setDelta(delta);
        event.setStatus(status);
        event.setExt(ext == null ? new LinkedHashMap<>() : new LinkedHashMap<>(ext));
        (eventPublisher == null ? ConversationEventPublisher.NOOP : eventPublisher).publish(event);
    }

    public void publishProgressEvent(String source, String phase, String message) {
        publishEvent(ConversationEventTypes.PROGRESS, source, phase, message,
                null, null, STATUS_RUNNING, null);
    }

    public void publishProgressEvent(String source, String phase, String message, Map<String, Object> ext) {
        publishEvent(ConversationEventTypes.PROGRESS, source, phase, message,
                null, null, STATUS_RUNNING, ext);
    }

    public void publishClarificationEvent(String source, String phase, String message) {
        publishEvent(ConversationEventTypes.CLARIFICATION, source, phase, message,
                null, null, "INPUT_REQUIRED", null);
    }

    public void publishErrorEvent(String source, String phase, String message) {
        publishEvent(ConversationEventTypes.ERROR, source, phase, message,
                null, null, "FAILED", null);
    }

    public void publishCompleteEvent(String source, String phase, String message, String answer, String status) {
        publishEvent(ConversationEventTypes.COMPLETE, source,
                StringUtils.hasText(phase) ? phase : ConversationEventPhases.COMPLETED,
                message, answer, null, status, null);
    }

    @Override
    public String toString() {
        return "ConversationRuntimeContext{" +
                "traceId='" + (command == null ? null : command.getTraceId()) + '\'' +
                ", sessionCode='" + (session == null ? null : session.getSessionCode()) + '\'' +
                ", roundCode='" + (round == null ? null : round.getRoundCode()) + '\'' +
                ", roundStatus='" + (round == null ? null : round.getStatus()) + '\'' +
                '}';
    }
}

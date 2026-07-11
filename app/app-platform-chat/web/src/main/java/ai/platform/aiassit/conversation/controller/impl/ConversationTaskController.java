package ai.platform.aiassit.conversation.controller.impl;

import ai.platform.aiassit.conversation.controller.IConversationTaskController;
import ai.platform.aiassit.conversation.dto.task.ConversationTaskQueryRequest;
import ai.platform.aiassit.conversation.dto.task.ConversationTaskStatusResponse;
import ai.platform.aiassit.conversation.dto.task.ConversationTaskStopRequest;
import ai.platform.aiassit.conversation.runtime.ConversationRunManager;
import ai.platform.aiassit.conversation.runtime.task.ConversationRunSnapshot;
import org.arthena.framework.common.constant.ErrCodeConstant;
import org.arthena.framework.common.context.SystemContext;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.security.api.model.UserContext;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RestController
public class ConversationTaskController implements IConversationTaskController {

    private final ConversationRunManager runManager;

    public ConversationTaskController(ConversationRunManager runManager) {
        this.runManager = runManager;
    }

    @Override
    public ConversationTaskStatusResponse status(@RequestBody ConversationTaskQueryRequest request) {
        Long userId = resolveCurrentUserId();
        Optional<ConversationRunSnapshot> run = runManager.find(
                request == null ? null : request.getRunId(),
                request == null ? null : request.getSessionCode(),
                request == null ? null : request.getRoundCode(),
                userId
        );
        return run.map(this::toResponse).orElseGet(ConversationTaskStatusResponse::new);
    }

    @Override
    public Boolean stop(@RequestBody ConversationTaskStopRequest request) {
        return runManager.cancel(
                request == null ? null : request.getRunId(),
                request == null ? null : request.getSessionCode(),
                request == null ? null : request.getRoundCode(),
                resolveCurrentUserId()
        );
    }

    private ConversationTaskStatusResponse toResponse(ConversationRunSnapshot run) {
        ConversationTaskStatusResponse response = new ConversationTaskStatusResponse();
        response.setRunId(run.runId());
        response.setOwnerNodeId(run.ownerNodeId());
        response.setRequestId(run.requestId());
        response.setSessionCode(run.sessionCode());
        response.setRoundCode(run.roundCode());
        response.setActive(run.active());
        response.setStatus(run.state() == null ? null : run.state().name());
        response.setTaskCodes(List.of(run.runId()));
        response.setCreatedAt(format(run.createdAt()));
        response.setStartedAt(format(run.startedAt()));
        response.setFinishedAt(format(run.finishedAt()));
        response.setError(run.error());
        return response;
    }

    private String format(Instant value) {
        return value == null ? null : value.toString();
    }

    private Long resolveCurrentUserId() {
        UserContext userContext = SystemContext.getUserContext();
        if (userContext != null && userContext.subject() != null) {
            return userContext.subject().userId();
        }
        throw BizException.of(ErrCodeConstant.LOGIN_FAILED);
    }
}

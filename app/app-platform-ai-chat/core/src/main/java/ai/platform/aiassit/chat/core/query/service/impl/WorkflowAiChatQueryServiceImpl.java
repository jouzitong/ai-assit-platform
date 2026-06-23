package ai.platform.aiassit.chat.core.query.service.impl;

import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryResponse;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryStreamEvent;
import ai.platform.aiassit.chat.core.query.service.AiChatQueryService;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowDefinition;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.chat.core.workflow.engine.IWorkflowEngine;
import lombok.extern.slf4j.Slf4j;
import org.athena.framework.security.api.model.UserContext;
import org.athena.framework.security.auth.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class WorkflowAiChatQueryServiceImpl implements AiChatQueryService {

    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final IWorkflowEngine workflowEngine;

    public WorkflowAiChatQueryServiceImpl(IWorkflowEngine workflowEngine) {
        this.workflowEngine = workflowEngine;
    }

    @Override
    public AiChatQueryResponse query(AiChatQueryCommand command) {
        return null;
    }

    @Override
    public SseEmitter queryStream(AiChatQueryCommand command) {
        SseEmitter emitter = new SseEmitter(0L);
        UserContext userContext = SecurityContextHolder.get();
        CompletableFuture.runAsync(() -> {
            try {
                SecurityContextHolder.set(userContext);
                handleQueryStream(command, emitter);
            } finally {
                SecurityContextHolder.clear();
            }
        });
        return emitter;
    }

    private void handleQueryStream(AiChatQueryCommand command, SseEmitter emitter) {
        WorkflowDefinition workflowDefinition = buildWorkflowDefinition();
        WorkflowContext workflowContext = buildWorkflowContext(command, workflowDefinition);
        workflowContext.setEmitter(emitter);
        try {
            sendInitEvent(emitter, workflowContext);
            workflowEngine.run(workflowContext);

            String error = workflowContext.get(WorkflowContextKeys.Common.ERROR);
            if (error != null) {
                throw new IllegalStateException(error);
            }

            AiChatQueryStreamEvent completeEvent = new AiChatQueryStreamEvent();
            completeEvent.setEventType("complete");
            completeEvent.setSessionCode(workflowContext.getSession() == null ? null : workflowContext.getSession().getSessionCode());
            completeEvent.setRoundCode(workflowContext.getRound() == null ? null : workflowContext.getRound().getRoundCode());
            completeEvent.setAnswer(workflowContext.getRenderedAnswer());
            completeEvent.setStatus(STATUS_SUCCESS);
            emitter.send(SseEmitter.event().name("complete").data(completeEvent));
            emitter.complete();
        } catch (Exception ex) {
            log.error("workflow query stream failed", ex);
            sendErrorEvent(emitter, workflowContext, ex);
            emitter.completeWithError(ex);
        }
    }

    private WorkflowContext buildWorkflowContext(AiChatQueryCommand command, WorkflowDefinition workflowDefinition) {
        WorkflowContext context = new WorkflowContext();
        context.setCommand(command);
        context.setWorkflowDefinition(workflowDefinition);
        context.setWorkflowCode(workflowDefinition == null ? "ai-chat-query-workflow" : workflowDefinition.getWorkflowCode());
        return context;
    }

    private WorkflowDefinition buildWorkflowDefinition() {
        Map<String, WorkflowNodeConfig> nodes = new LinkedHashMap<>();
        nodes.put(WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode(), new WorkflowNodeConfig(WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode(), WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(), java.util.List.of()));
        nodes.put(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(), new WorkflowNodeConfig(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(), WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), java.util.List.of()));
        nodes.put(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), new WorkflowNodeConfig(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), WorkflowNodeCodes.RENDER.getNodeCode(), java.util.List.of()));
        nodes.put(WorkflowNodeCodes.SQL_VALIDATE.getNodeCode(), new WorkflowNodeConfig(WorkflowNodeCodes.SQL_VALIDATE.getNodeCode(), WorkflowNodeCodes.SQL_EXECUTE.getNodeCode(), java.util.List.of()));
        nodes.put(WorkflowNodeCodes.SQL_EXECUTE.getNodeCode(), new WorkflowNodeConfig(WorkflowNodeCodes.SQL_EXECUTE.getNodeCode(), WorkflowNodeCodes.RENDER.getNodeCode(), java.util.List.of()));
        nodes.put(WorkflowNodeCodes.RENDER.getNodeCode(), new WorkflowNodeConfig(WorkflowNodeCodes.RENDER.getNodeCode(), null, java.util.List.of()));
        return new WorkflowDefinition("ai-chat-query-workflow", nodes, WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode());
    }

    private void sendInitEvent(SseEmitter emitter, WorkflowContext workflowContext) throws IOException {
        AiChatQueryStreamEvent initEvent = new AiChatQueryStreamEvent();
        initEvent.setEventType("init");
        initEvent.setSessionCode(workflowContext.getCommand() == null ? null : workflowContext.getCommand().getSessionCode());
        initEvent.setStatus(STATUS_RUNNING);
        emitter.send(SseEmitter.event().name("init").data(initEvent));
    }

    private void sendErrorEvent(SseEmitter emitter, WorkflowContext workflowContext, Exception ex) {
        AiChatQueryStreamEvent errorEvent = new AiChatQueryStreamEvent();
        errorEvent.setEventType("error");
        errorEvent.setSessionCode(workflowContext.getSession() == null ? null : workflowContext.getSession().getSessionCode());
        errorEvent.setRoundCode(workflowContext.getRound() == null ? null : workflowContext.getRound().getRoundCode());
        errorEvent.setStatus(STATUS_FAILED);
        errorEvent.setMessage(ex.getMessage());
        try {
            emitter.send(SseEmitter.event().name("error").data(errorEvent));
        } catch (IOException ioException) {
            log.warn("failed to send workflow error event", ioException);
        }
    }
}

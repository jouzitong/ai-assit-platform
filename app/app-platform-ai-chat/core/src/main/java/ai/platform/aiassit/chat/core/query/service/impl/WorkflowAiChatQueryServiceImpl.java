package ai.platform.aiassit.chat.core.query.service.impl;

import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryResponse;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryStreamEvent;
import ai.platform.aiassit.chat.core.query.service.AiChatQueryService;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowDefinition;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
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
        WorkflowContext workflowContext = buildWorkflowContext(command);
        workflowContext.setEmitter(emitter);
        try {
            sendInitEvent(emitter, workflowContext);
            workflowEngine.run(buildWorkflowDefinition(), workflowContext);

            String error = workflowContext.get("error");
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

    private WorkflowContext buildWorkflowContext(AiChatQueryCommand command) {
        WorkflowContext context = new WorkflowContext();
        context.setCommand(command);
        context.setWorkflowCode("ai-chat-query-workflow");
        return context;
    }

    private WorkflowDefinition buildWorkflowDefinition() {
        Map<String, WorkflowNodeConfig> nodes = new LinkedHashMap<>();
        nodes.put("chat-message", new WorkflowNodeConfig("chat-message", "Chat-Message", "query-planning", java.util.List.of()));
        nodes.put("query-planning", new WorkflowNodeConfig("query-planning", "Query-Planning", "knowledge-search", java.util.List.of()));
        nodes.put("knowledge-search", new WorkflowNodeConfig("knowledge-search", "Knowledge-Search", "sql-generate", java.util.List.of()));
        nodes.put("sql-generate", new WorkflowNodeConfig("sql-generate", "Sql-Generate", "sql-validate", java.util.List.of()));
        nodes.put("sql-validate", new WorkflowNodeConfig("sql-validate", "Sql-Validate", "sql-execute", java.util.List.of()));
        nodes.put("sql-execute", new WorkflowNodeConfig("sql-execute", "Sql-Execute", "render", java.util.List.of()));
        nodes.put("render", new WorkflowNodeConfig("render", "Render", null, java.util.List.of()));
        return new WorkflowDefinition("ai-chat-query-workflow", nodes, "chat-message");
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

package ai.platform.aiassit.chat.core.query.service.impl;

import ai.platform.aiassit.chat.core.query.dto.AiChatQueryCommand;
import ai.platform.aiassit.chat.core.query.dto.AiChatQueryResponse;
import ai.platform.aiassit.chat.core.query.service.AiChatQueryService;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowDefinition;
import ai.platform.aiassit.chat.core.workflow.bean.WorkflowNodeConfig;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowContext;
import ai.platform.aiassit.chat.core.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.chat.core.workflow.engine.IWorkflowEngine;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WorkflowAiChatQueryServiceImpl implements AiChatQueryService {

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
        handleQueryStream(command, emitter);
        return emitter;
    }

    private void handleQueryStream(AiChatQueryCommand command, SseEmitter emitter) {
        WorkflowDefinition workflowDefinition = buildWorkflowDefinition();
        WorkflowContext workflowContext = buildWorkflowContext(command, workflowDefinition);
        workflowContext.setEmitter(emitter);
        workflowEngine.run(workflowContext);
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
        nodes.put(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(), new WorkflowNodeConfig(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(), WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), java.util.List.of()));
        nodes.put(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), new WorkflowNodeConfig(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), WorkflowNodeCodes.RENDER.getNodeCode(), java.util.List.of()));
        nodes.put(WorkflowNodeCodes.RENDER.getNodeCode(), new WorkflowNodeConfig(WorkflowNodeCodes.RENDER.getNodeCode(), null, java.util.List.of()));
        return new WorkflowDefinition("ai-chat-query-workflow", nodes, WorkflowNodeCodes.QUERY_PLANNING.getNodeCode());
    }
}

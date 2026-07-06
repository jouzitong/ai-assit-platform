package ai.platform.aiassit.conversation.service.impl;

import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.enums.AiChatRoundType;
import ai.platform.aiassit.chat.history.service.AiChatRoundService;
import ai.platform.aiassit.conversation.constant.ConversationEventPhases;
import ai.platform.aiassit.conversation.constant.ConversationEventSources;
import ai.platform.aiassit.conversation.workflow.constants.WorkflowContextKeys;
import ai.platform.aiassit.conversation.workflow.context.WorkflowContext;
import ai.platform.aiassit.conversation.workflow.context.WorkflowNodeCodes;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.conversation.workflow.service.WorkflowDefinitionFactory;
import ai.platform.aiassit.conversation.workflow.service.WorkflowIntentAnalyzeService;
import ai.platform.aiassit.service.ai.api.dto.IntentAnalyzeResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
public class ConversationIntentRouteService {

    private static final String SIMPLE_CHAT = "SIMPLE_CHAT";

    private final WorkflowIntentAnalyzeService workflowIntentAnalyzeService;
    private final WorkflowDefinitionFactory workflowDefinitionFactory;
    private final AiChatRoundService roundService;

    public ConversationIntentRouteService(WorkflowIntentAnalyzeService workflowIntentAnalyzeService,
                                    WorkflowDefinitionFactory workflowDefinitionFactory,
                                    AiChatRoundService roundService) {
        this.workflowIntentAnalyzeService = workflowIntentAnalyzeService;
        this.workflowDefinitionFactory = workflowDefinitionFactory;
        this.roundService = roundService;
    }

    public void route(WorkflowContext context) {
        IntentAnalyzeResponse response = resolveIntentAnalyzeResponse(context);
        refreshRoundType(context, response);
        bindWorkflowDefinition(context, response);
    }

    private IntentAnalyzeResponse resolveIntentAnalyzeResponse(WorkflowContext context) {
        IntentAnalyzeResponse existingResponse = context.get(WorkflowContextKeys.Planning.INTENT_ANALYZE_RESPONSE);
        if (existingResponse != null) {
            return existingResponse;
        }
        try {
            IntentAnalyzeResponse response = workflowIntentAnalyzeService.analyze(context);
            if (response == null) {
                return null;
            }
            context.put(WorkflowContextKeys.Planning.INTENT_ANALYZE_RESPONSE, response);
            context.putNodeOutput(WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode(), "intentAnalyzeResponse", response);
            context.publishProgressEvent(
                    ConversationEventSources.INTENT_ANALYZE,
                    ConversationEventPhases.READY,
                    "base intent analysis prepared",
                    Map.of("intentType", response.getIntentType())
            );
            return response;
        } catch (Exception ex) {
            log.warn("base intent analyze failed, sessionCode={}, roundCode={}",
                    context.getSession() == null ? null : context.getSession().getSessionCode(),
                    context.getRound() == null ? null : context.getRound().getRoundCode(),
                    ex);
            context.put(WorkflowContextKeys.Planning.INTENT_ANALYZE_ERROR, ex.getMessage());
            context.publishProgressEvent(
                    ConversationEventSources.INTENT_ANALYZE,
                    ConversationEventPhases.SKIPPED,
                    "base intent analysis skipped"
            );
            return null;
        }
    }

    private void bindWorkflowDefinition(WorkflowContext context, IntentAnalyzeResponse response) {
        String intentType = response == null ? null : response.getIntentType();
        if (SIMPLE_CHAT.equalsIgnoreCase(intentType)) {
            context.setWorkflowDefinition(workflowDefinitionFactory.simpleChatWorkflow());
        } else {
            context.setWorkflowDefinition(workflowDefinitionFactory.queryRenderWorkflow());
        }
        if (context.getWorkflowDefinition() != null) {
            context.setWorkflowCode(context.getWorkflowDefinition().getWorkflowCode());
        }
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("workflowCode", context.getWorkflowCode());
        ext.put("intentType", intentType == null ? "QUERY_RENDER" : intentType);
        context.publishProgressEvent(
                ConversationEventSources.INTENT_ANALYZE,
                ConversationEventPhases.READY,
                "workflow routed",
                ext
        );
    }

    private void refreshRoundType(WorkflowContext context, IntentAnalyzeResponse response) {
        if (hasExplicitRoundType(context == null ? null : context.getCommand())) {
            return;
        }
        AiChatRoundDTO round = context == null ? null : context.getRound();
        if (round == null || round.getId() == null) {
            return;
        }
        AiChatRoundType roundType = AiChatRoundType.fromIntentType(response == null ? null : response.getIntentType());
        if (roundType == round.getRoundType()) {
            return;
        }
        AiChatRoundDTO update = new AiChatRoundDTO();
        update.setRoundType(roundType);
        roundService.edit(round.getId(), update);
        round.setRoundType(roundType);
    }

    private boolean hasExplicitRoundType(ConversationQueryCommand command) {
        return readExtText(command, "roundType") != null || readExtText(command, "intentType") != null;
    }

    private String readExtText(ConversationQueryCommand command, String key) {
        Object value = command == null || command.getExt() == null ? null : command.getExt().get(key);
        if (value instanceof String str && StringUtils.hasText(str)) {
            return str.trim();
        }
        return null;
    }
}

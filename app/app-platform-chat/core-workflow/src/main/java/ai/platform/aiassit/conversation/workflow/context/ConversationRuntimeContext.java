package ai.platform.aiassit.conversation.workflow.context;

import ai.platform.aiassit.conversation.constant.ConversationEventPhases;
import ai.platform.aiassit.conversation.constant.ConversationEventTypes;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationQueryCommand;
import ai.platform.aiassit.conversation.workflow.dto.ConversationQueryStreamEvent;
import ai.platform.aiassit.conversation.workflow.bean.WorkflowDefinition;
import ai.platform.aiassit.conversation.workflow.runtime.ConversationCancellation;
import ai.platform.aiassit.conversation.workflow.runtime.ConversationEventPublisher;
import ai.platform.aiassit.chat.history.entity.dto.AiChatArtifactDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatRoundDTO;
import ai.platform.aiassit.chat.history.entity.dto.AiChatSessionDTO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 聊天运行上下文。
 *
 * <p>用于承载一次聊天任务执行过程中的输入参数、会话信息、意图分析结果、工作流定义和节点中间结果。</p>
 *
 * <p>workflow 相关状态只是该运行上下文中的一个执行域，而不是上下文本身的全部语义。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
@Data
@Slf4j
public class ConversationRuntimeContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String SQL_STAGE_NODE_CODE = WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode();

    /**
     * 工作流事件默认运行中状态。
     */
    private static final String STATUS_RUNNING = "RUNNING";

    /**
     * 当前对话查询命令，包含用户输入、会话标识、追踪 ID 等请求参数。
     */
    private ConversationQueryCommand command;

    /**
     * 当前聊天任务绑定的工作流编码。
     */
    private String workflowCode;

    /**
     * 当前聊天任务绑定的工作流定义。
     */
    private WorkflowDefinition workflowDefinition;

    /**
     * 当前对话会话信息。
     */
    private AiChatSessionDTO session;

    /**
     * 当前会话下的历史产物列表，例如生成的 SQL、报表、图表或其他结构化结果。
     */
    private List<AiChatArtifactDTO> sessionArtifacts = new ArrayList<>();

    /**
     * 用户消息上下文，统一管理当前输入、历史用户输入及其汇总说明。
     */
    private UserMessageContext userMessageContext = new UserMessageContext();

    /**
     * 工作流结果上下文，统一承载动态节点结果。
     */
    private WorkflowResultContext resultContext = new WorkflowResultContext();

    /**
     * 执行事件发布端口，具体传输方式由上层运行时决定。
     */
    private transient ConversationEventPublisher eventPublisher = ConversationEventPublisher.NOOP;

    /**
     * 当前运行的协作式取消信号。
     */
    private transient ConversationCancellation cancellation = ConversationCancellation.NONE;

    /**
     * 扩展数据容器，用于存放各节点临时产生的非固定结构数据。
     */
    private Map<String, Object> data = new HashMap<>();

    /**
     * 写入扩展数据。
     *
     * @param key   数据键
     * @param value 数据值
     */
    public void put(String key, Object value) {
        data.put(key, value);
    }

    /**
     * 获取扩展数据。
     *
     * @param key 数据键
     * @param <T> 返回值类型
     * @return 指定键对应的数据值
     */
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    /**
     * 获取或初始化用户消息上下文。
     *
     * @return 用户消息上下文
     */
    public UserMessageContext getOrCreateUserMessageContext() {
        if (userMessageContext == null) {
            userMessageContext = new UserMessageContext();
        }
        return userMessageContext;
    }

    /**
     * 获取或初始化结果上下文。
     *
     * @return 结果上下文
     */
    public WorkflowResultContext getOrCreateResultContext() {
        if (resultContext == null) {
            resultContext = new WorkflowResultContext();
        }
        return resultContext;
    }

    /**
     * 获取或初始化指定节点的结果上下文。
     *
     * @param nodeCode 节点编码
     * @return 节点结果上下文
     */
    public WorkflowNodeResult getOrCreateNodeResult(String nodeCode) {
        WorkflowResultContext context = getOrCreateResultContext();
        WorkflowNodeResult nodeResult = context.getNodeResults().computeIfAbsent(nodeCode, key -> {
            WorkflowNodeResult result = new WorkflowNodeResult();
            result.setNodeCode(nodeCode);
            return result;
        });
        return nodeResult;
    }

    public void putNodeOutput(String nodeCode, String key, Object value) {
        getOrCreateNodeResult(nodeCode).getOutputs().put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getNodeOutput(String nodeCode, String key) {
        WorkflowNodeResult nodeResult = getOrCreateResultContext().getNodeResults().get(nodeCode);
        if (nodeResult == null || nodeResult.getOutputs() == null) {
            return null;
        }
        return (T) nodeResult.getOutputs().get(key);
    }

    public void putNodeMetadata(String nodeCode, String key, Object value) {
        getOrCreateNodeResult(nodeCode).getMetadata().put(key, value);
    }

    /**
     * 根据当前会话消息和当前用户输入刷新用户消息上下文。
     *
     * <p>当前约定只在会话初始化阶段调用，用于初始化本轮用户消息上下文。</p>
     */
    public void refreshUserMessageContext() {
        UserMessageContext messageContext = getOrCreateUserMessageContext();
        List<AiChatMessageDTO> sessionMessages = messageContext.getSessionMessages() == null
                ? List.of()
                : messageContext.getSessionMessages();
        messageContext.setSessionMessages(new ArrayList<>(sessionMessages));
        if (messageContext.getInvalidIntents() == null) {
            messageContext.setInvalidIntents(new ArrayList<>());
        }
        messageContext.setSummary(buildUserMessageSummary(messageContext.getCurrentMessage(), buildHistoricalUserMessages()));
    }

    /**
     * 获取当前用户输入之前的历史用户消息。
     *
     * @return 历史用户消息列表
     */
    public List<AiChatMessageDTO> resolveHistoricalUserMessages() {
        UserMessageContext messageContext = getOrCreateUserMessageContext();
        return new ArrayList<>(buildHistoricalUserMessages());
    }

    private List<AiChatMessageDTO> buildHistoricalUserMessages() {
        UserMessageContext messageContext = getOrCreateUserMessageContext();
        List<AiChatMessageDTO> sessionMessages = messageContext.getSessionMessages();
        if (sessionMessages == null || sessionMessages.isEmpty()) {
            return List.of();
        }
        return sessionMessages.stream()
                .filter(Objects::nonNull)
                .filter(message -> messageContext.getCurrentMessage() == null
                        || !Objects.equals(message.getMessageCode(), messageContext.getCurrentMessage().getMessageCode()))
                .filter(message -> "USER".equalsIgnoreCase(message.getRole()))
                .filter(message -> hasText(message.getContent()))
                .sorted(Comparator.comparing(AiChatMessageDTO::getSortNo, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private String buildUserMessageSummary(AiChatMessageDTO currentMessage, List<AiChatMessageDTO> historyMessages) {
        StringBuilder builder = new StringBuilder();
        if (hasText(currentMessage == null ? null : currentMessage.getContent())) {
            builder.append("当前用户输入：").append(currentMessage.getContent().trim());
        }
        if (historyMessages != null && !historyMessages.isEmpty()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append("历史用户输入：");
            for (int i = 0; i < historyMessages.size(); i++) {
                AiChatMessageDTO message = historyMessages.get(i);
                if (!hasText(message == null ? null : message.getContent())) {
                    continue;
                }
                builder.append('\n').append(i + 1).append(". ").append(message.getContent().trim());
            }
        }
        return builder.toString().trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public AiChatRoundDTO getRound() {
        return getOrCreateResultContext().getRound();
    }

    public void setRound(AiChatRoundDTO round) {
        getOrCreateResultContext().setRound(round);
        putNodeOutput(WorkflowNodeCodes.CHAT_MESSAGE.getNodeCode(), "round", round);
    }

    public String getAnalysisResult() {
        return getNodeOutput(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(), "analysisResult");
    }

    public void setAnalysisResult(String analysisResult) {
        putNodeOutput(WorkflowNodeCodes.QUERY_PLANNING.getNodeCode(), "analysisResult", analysisResult);
    }

    public String getKnowledgeBaseId() {
        return getNodeOutput(WorkflowNodeCodes.KNOWLEDGE_SEARCH.getNodeCode(), "knowledgeBaseId");
    }

    public void setKnowledgeBaseId(String knowledgeBaseId) {
        putNodeOutput(WorkflowNodeCodes.KNOWLEDGE_SEARCH.getNodeCode(), "knowledgeBaseId", knowledgeBaseId);
    }

    public String getKnowledgeResult() {
        return getNodeOutput(WorkflowNodeCodes.KNOWLEDGE_SEARCH.getNodeCode(), "knowledgeResult");
    }

    public void setKnowledgeResult(String knowledgeResult) {
        putNodeOutput(WorkflowNodeCodes.KNOWLEDGE_SEARCH.getNodeCode(), "knowledgeResult", knowledgeResult);
    }

    public String getPromptContext(String nodeCode) {
        return getNodeOutput(nodeCode, "promptContext");
    }

    public void setPromptContext(String nodeCode, String promptContext) {
        putNodeOutput(nodeCode, "promptContext", promptContext);
    }

    public String getGeneratedSql() {
        return getNodeOutput(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), "generatedSql");
    }

    public <T> T getSqlPreGenerateResult() {
        return getNodeOutput(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), "sqlPreGenerateResult");
    }

    public void setSqlPreGenerateResult(Object sqlPreGenerateResult) {
        putNodeOutput(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), "sqlPreGenerateResult", sqlPreGenerateResult);
    }

    public void setGeneratedSql(String generatedSql) {
        putNodeOutput(WorkflowNodeCodes.SQL_PRE_GENERATE.getNodeCode(), "generatedSql", generatedSql);
    }

    public String getValidatedSql() {
        return getNodeOutput(SQL_STAGE_NODE_CODE, "validatedSql");
    }

    public void setValidatedSql(String validatedSql) {
        putNodeOutput(SQL_STAGE_NODE_CODE, "validatedSql", validatedSql);
    }

    public String getSqlValidationError() {
        return getNodeOutput(SQL_STAGE_NODE_CODE, "sqlValidationError");
    }

    public void setSqlValidationError(String sqlValidationError) {
        putNodeOutput(SQL_STAGE_NODE_CODE, "sqlValidationError", sqlValidationError);
    }

    public String getSqlExecutionStatus() {
        return getNodeOutput(SQL_STAGE_NODE_CODE, "sqlExecutionStatus");
    }

    public void setSqlExecutionStatus(String sqlExecutionStatus) {
        putNodeOutput(SQL_STAGE_NODE_CODE, "sqlExecutionStatus", sqlExecutionStatus);
    }

    public Object getSqlExecutionResult() {
        return getNodeOutput(SQL_STAGE_NODE_CODE, "sqlExecutionResult");
    }

    public void setSqlExecutionResult(Object sqlExecutionResult) {
        putNodeOutput(SQL_STAGE_NODE_CODE, "sqlExecutionResult", sqlExecutionResult);
    }

    public String getRenderedAnswer() {
        String answer = getNodeOutput(WorkflowNodeCodes.RENDER.getNodeCode(), "renderedAnswer");
        if (hasText(answer)) {
            return answer;
        }
        return getNodeOutput(WorkflowNodeCodes.SIMPLE_CHAT.getNodeCode(), "renderedAnswer");
    }

    public void setRenderedAnswer(String renderedAnswer) {
        String existingRenderAnswer = getNodeOutput(WorkflowNodeCodes.RENDER.getNodeCode(), "renderedAnswer");
        if (hasText(existingRenderAnswer)
                || getOrCreateResultContext().getNodeResults().containsKey(WorkflowNodeCodes.RENDER.getNodeCode())) {
            putNodeOutput(WorkflowNodeCodes.RENDER.getNodeCode(), "renderedAnswer", renderedAnswer);
            return;
        }
        putNodeOutput(WorkflowNodeCodes.SIMPLE_CHAT.getNodeCode(), "renderedAnswer", renderedAnswer);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> getRenderJson() {
        return getNodeOutput(WorkflowNodeCodes.RENDER.getNodeCode(), "renderJson");
    }

    public void setRenderJson(Map<String, Object> renderJson) {
        putNodeOutput(WorkflowNodeCodes.RENDER.getNodeCode(), "renderJson", renderJson);
    }

    public String getRenderCheckReport() {
        return getNodeOutput(WorkflowNodeCodes.RENDER.getNodeCode(), "renderCheckReport");
    }

    public void setRenderCheckReport(String renderCheckReport) {
        putNodeOutput(WorkflowNodeCodes.RENDER.getNodeCode(), "renderCheckReport", renderCheckReport);
    }

    /**
     * 推送默认运行中状态的工作流事件。
     *
     * @param eventType 会话事件类型
     * @param source    事件来源
     * @param phase     事件阶段
     * @param message   事件消息
     */
    public void publishEvent(String eventType, String source, String phase, String message) {
        publishEvent(eventType, source, phase, message, null, null, STATUS_RUNNING, null);
    }

    /**
     * 推送工作流执行事件到前端。
     *
     * <p>该方法会组装当前请求、会话、轮次以及消息内容，并通过 SSE 发送给前端。</p>
     *
     * @param eventType 会话事件类型
     * @param source    事件来源
     * @param phase     事件阶段
     * @param message   事件消息
     * @param answer    当前完整答案内容
     * @param delta     本次增量输出内容
     * @param status    当前事件状态
     * @param ext       扩展上下文
     */
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
        event.setRoundCode(getRound() == null ? null : getRound().getRoundCode());
        event.setMessage(message);
        event.setAnswer(answer);
        event.setDelta(delta);
        event.setStatus(status);
        event.setExt(ext == null ? new LinkedHashMap<>() : new LinkedHashMap<>(ext));
        ConversationEventPublisher publisher = eventPublisher == null ? ConversationEventPublisher.NOOP : eventPublisher;
        publisher.publish(event);
    }

    public void checkCancellation() {
        ConversationCancellation signal = cancellation == null ? ConversationCancellation.NONE : cancellation;
        signal.throwIfCancellationRequested();
    }

    public void publishProgressEvent(String source, String phase, String message) {
        publishEvent(ConversationEventTypes.PROGRESS, source, phase, message, null, null, STATUS_RUNNING, null);
    }

    public void publishProgressEvent(String source, String phase, String message, Map<String, Object> ext) {
        publishEvent(ConversationEventTypes.PROGRESS, source, phase, message, null, null, STATUS_RUNNING, ext);
    }

    public void publishActivityEvent(String source,
                                     String phase,
                                     String message,
                                     String status,
                                     Map<String, Object> ext) {
        publishEvent(ConversationEventTypes.PROGRESS, "ACTIVITY", source, phase, message,
                null, null, StringUtils.hasText(status) ? status : STATUS_RUNNING, ext);
    }

    public void publishAnswerEvent(String source, String phase, String message, String answer, String delta, String status) {
        publishEvent(StringUtils.hasText(delta) ? ConversationEventTypes.ANSWER_DELTA : ConversationEventTypes.ANSWER,
                source, phase, message, answer, delta, status, null);
    }

    public void publishAnswerEvent(String source,
                                   String phase,
                                   String message,
                                   String answer,
                                   String delta,
                                   String status,
                                   Map<String, Object> ext) {
        publishEvent(StringUtils.hasText(delta) ? ConversationEventTypes.ANSWER_DELTA : ConversationEventTypes.ANSWER,
                source, phase, message, answer, delta, status, ext);
    }

    public void publishClarificationEvent(String source, String phase, String message) {
        publishEvent(ConversationEventTypes.CLARIFICATION, source, phase, message, null, null, STATUS_RUNNING, null);
    }

    public void publishErrorEvent(String source, String phase, String message) {
        publishEvent(ConversationEventTypes.ERROR, source, phase, message, null, null, "FAILED", null);
    }

    public void publishCompleteEvent(String source, String phase, String message, String answer, String status) {
        publishEvent(ConversationEventTypes.COMPLETE,
                source,
                StringUtils.hasText(phase) ? phase : ConversationEventPhases.COMPLETED,
                message,
                answer,
                null,
                status,
                null);
    }

}

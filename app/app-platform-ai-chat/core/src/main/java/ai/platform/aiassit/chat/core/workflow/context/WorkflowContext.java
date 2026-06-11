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
 * 工作流上下文。
 *
 * <p>用于承载一次 AI 对话工作流执行过程中的输入参数、会话信息、节点中间结果、SQL 执行结果以及 SSE 推送对象。</p>
 *
 * @author zhouzhitong
 * @since 2026/6/8
 */
@Data
@Slf4j
public class WorkflowContext implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工作流事件默认运行中状态。
     */
    private static final String STATUS_RUNNING = "RUNNING";

    /**
     * 当前对话查询命令，包含用户输入、会话标识、追踪 ID 等请求参数。
     */
    private AiChatQueryCommand command;

    /**
     * 当前执行的工作流编码。
     */
    private String workflowCode;

    /**
     * 当前对话会话信息。
     */
    private AiChatSessionDTO session;

    /**
     * 当前会话下的历史消息列表。
     */
    private List<AiChatMessageDTO> sessionMessages = new ArrayList<>();

    /**
     * 当前会话下的历史产物列表，例如生成的 SQL、报表、图表或其他结构化结果。
     */
    private List<AiChatArtifactDTO> sessionArtifacts = new ArrayList<>();

    /**
     * 当前轮次的用户消息。
     */
    private AiChatMessageDTO currentUserMessage;

    /**
     * 当前对话轮次信息。
     */
    private AiChatRoundDTO round;

    /**
     * 发送给 AI 引擎的请求参数。
     */
    private ChatRequest engineRequest;

    /**
     * AI 引擎返回的响应结果。
     */
    private ChatResponse engineResponse;

    /**
     * 用户问题的意图分析结果。
     */
    private String analysisResult;

    /**
     * 当前查询使用的知识库 ID。
     */
    private String knowledgeBaseId;

    /**
     * 知识库检索结果，用于辅助后续 SQL 生成或答案生成。
     */
    private String knowledgeResult;

    /**
     * AI 生成的原始 SQL。
     */
    private String generatedSql;

    /**
     * 校验通过后可执行的 SQL。
     */
    private String validatedSql;

    /**
     * SQL 校验失败时的错误信息。
     */
    private String sqlValidationError;

    /**
     * SQL 执行状态。
     */
    private String sqlExecutionStatus;

    /**
     * SQL 执行结果。
     */
    private Object sqlExecutionResult;

    /**
     * 最终渲染给用户的答案内容。
     */
    private String renderedAnswer;

    /**
     * SSE 推送对象，用于向前端实时推送工作流执行过程事件。
     */
    private transient SseEmitter emitter;

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
     * 推送默认运行中状态的工作流事件。
     *
     * @param eventType 事件类型
     * @param message   事件消息
     */
    public void publishEvent(String eventType, String message) {
        publishEvent(eventType, message, null, null, STATUS_RUNNING);
    }

    /**
     * 推送工作流执行事件到前端。
     *
     * <p>该方法会组装当前请求、会话、轮次以及消息内容，并通过 SSE 发送给前端。</p>
     *
     * @param eventType 事件类型
     * @param message   事件消息
     * @param answer    当前完整答案内容
     * @param delta     本次增量输出内容
     * @param status    当前事件状态
     */
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

package ai.platform.aiassit.conversation.workflow.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class ConversationQueryStreamEvent {

    /** 协议版本。 */
    private String protocolVersion = "1.0";

    /** 当前运行实例编码。 */
    private String runId;

    /** 运行内单调递增的事件序号，用于断线重放。 */
    private String eventId;

    /** 事件产生时间戳。 */
    private Long timestamp;

    /**
     * 业务事件类型。
     *
     * <p>用于前端做一级事件分发。当前协议约定的主要取值包括：
     * progress、answer_delta、answer、clarification、error、complete。</p>
     */
    private String eventType;

    /**
     * 进度子类型。
     *
     * <p>仅当 {@link #eventType} 为 progress 时使用，用于区分 progress 内部的进度语义。
     * 推荐取值：</p>
     *
     * <ul>
     *     <li>PLAN：计划进度，声明本轮目标、Artifact Workflow 和 Agent 协作计划。</li>
     *     <li>AGENT：Agent 协作进度，更新某个 Agent 的生命周期状态。</li>
     *     <li>ACTIVITY：活动进度，更新某个 Agent 内部具体活动的执行状态。</li>
     * </ul>
     */
    private String progressType;

    /**
     * 事件来源。
     *
     * <p>表示产生该事件的业务域或 Agent Runtime，例如 CONVERSATION、AI_AGENT。</p>
     *
     * <p>前端不应只依赖 source 判断事件大类，一级分发仍应以 {@link #eventType} 为准。</p>
     */
    private String source;

    /**
     * 事件阶段。
     *
     * <p>表示当前来源或当前进度对象的执行阶段。推荐取值包括：
     * STARTED、RUNNING、READY、COMPLETED、SKIPPED、FAILED。</p>
     */
    private String phase;

    /**
     * 本次请求链路追踪 ID。
     *
     * <p>用于串联一次流式请求中的日志、事件和后端调用链路。</p>
     */
    private String requestId;

    /**
     * 会话编码。
     *
     * <p>新建会话时由后端生成并通过初始化事件返回；继续已有会话时表示当前会话。</p>
     */
    private String sessionCode;

    /**
     * 会话名称。
     *
     * <p>用于前端展示当前会话标题。新建会话时可能由用户输入、模型摘要或后端规则生成。</p>
     */
    private String sessionName;

    /**
     * 本轮对话编码。
     *
     * <p>用于标识一次用户输入触发的完整执行轮次，前端 reconnect、刷新详情和关联产物时可使用该字段。</p>
     */
    private String roundCode;

    /**
     * 增量回答内容。
     *
     * <p>仅用于 eventType=answer_delta 的 token 级或片段级输出。前端收到后应追加到当前临时回答中。</p>
     */
    private String delta;

    /**
     * 当前完整回答或最终回答快照。
     *
     * <p>用于 eventType=answer 或 complete。对于 Render JSON 场景，answer 可承载序列化后的渲染 JSON。</p>
     */
    private String answer;

    /**
     * 当前事件状态。
     *
     * <p>表示该事件对应对象的状态，例如 RUNNING、SUCCESS、FAILED。progressType=AGENT 时表示 Agent 状态，
     * progressType=ACTIVITY 时表示 Agent 内活动状态，complete 时表示整轮状态。</p>
     */
    private String status;

    /**
     * 面向前端状态展示或错误展示的文本。
     *
     * <p>该字段适合放简短可读说明，不应作为前端解析业务状态的唯一依据；结构化数据应放在 {@link #ext}。</p>
     */
    private String message;

    /**
     * 扩展字段。
     *
     * <p>用于承载不同事件类型的结构化业务数据。常见内容包括：</p>
     *
     * <ul>
     *     <li>progressType=AGENT：agentCode、agentName、durationMs、error 等协作信息。</li>
     *     <li>progressType=ACTIVITY：agentCode、activity、artifacts、usage、durationMs 等活动信息。</li>
     *     <li>eventType=answer：contentFormat、artifactType、renderPageCode 等回答产物信息。</li>
     * </ul>
     *
     * <p>新增结构化字段时应优先保持 key 稳定，避免前端依赖 message 文案解析业务含义。</p>
     */
    private Map<String, Object> ext = new LinkedHashMap<>();

    /**
     * 日志摘要：仅输出事件定位与状态字段，不输出 answer、delta、message 或 ext。
     */
    @Override
    public String toString() {
        return "ConversationQueryStreamEvent{" +
                "runId='" + runId + '\'' +
                ", eventId='" + eventId + '\'' +
                ", eventType='" + eventType + '\'' +
                ", progressType='" + progressType + '\'' +
                ", source='" + source + '\'' +
                ", phase='" + phase + '\'' +
                ", requestId='" + requestId + '\'' +
                ", sessionCode='" + sessionCode + '\'' +
                ", roundCode='" + roundCode + '\'' +
                ", status='" + status + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}

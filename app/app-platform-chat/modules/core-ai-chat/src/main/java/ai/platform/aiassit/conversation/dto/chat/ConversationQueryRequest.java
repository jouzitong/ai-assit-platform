package ai.platform.aiassit.conversation.dto.chat;

import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationAttachmentDTO;
import ai.platform.aiassit.conversation.workflow.dto.chat.ConversationToolDTO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 对话查询对外请求参数。
 *
 * <p>该对象是 ai-chat 对外暴露的 Web 请求 DTO，用于接收前端提交的最小对话输入。
 * 与内部使用的 {@code ConversationQueryCommand} 不同，这个类只保留前端真正应该传入的字段，
 * 避免把用户身份、链路信息、执行策略等服务端控制参数暴露给调用方。</p>
 *
 * <p>设计原则如下：</p>
 * <p>1. 前端只传“用户输入”和“用户显式选择”的内容。</p>
 * <p>2. 用户身份、traceId、scene、默认会话名等由 controller 或服务端自动补齐。</p>
 * <p>3. 聊天始终路由到 HOME_CHAT Agent；modelId 表示用户从系统已启用模型中作出的选择。</p>
 * <p>4. 文件、工具等请求信息不能扩大已发布 Agent Snapshot 授予的能力。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ConversationQueryRequest extends BaseRequest {

    /**
     * 会话编码。
     *
     * <p>用于指定本次提问所属的已有会话。</p>
     * <p>当前端继续某个历史会话时传入；为空时表示开启一个新会话，由服务端自动创建新的会话并生成 sessionCode。</p>
     */
    private String sessionCode;

    /** 用户从系统已启用模型中选择的配置 ID，连接信息始终由服务端解析。 */
    private Long modelId;

    /**
     * 用户输入的消息内容。
     *
     * <p>这是本次对话请求的核心业务参数，对应输入框中的文本内容。</p>
     * <p>通常不能为空；后端会基于该字段创建用户消息、构造 AI 对话请求，并在需要时自动生成默认会话名称。</p>
     */
    private String message;

    /**
     * 附件列表。
     *
     * <p>用于传递本次消息关联的文件、图片或其他外部资源引用。</p>
     * <p>建议传文件引用信息而不是大文件原文，例如 fileId、fileName、fileUrl、mediaType。
     * 当前该字段主要作为能力扩展入口，由后端汇总后继续透传给后续执行链路。</p>
     */
    private List<ConversationAttachmentDTO> attachments = new ArrayList<>();

    /**
     * 工具列表。
     *
     * <p>旧调用方可携带的工具提示；正式 Agent 运行只允许使用已发布 Snapshot 和本轮 Grant
     * 同时授权的 Tool，该字段不能新增能力。</p>
     */
    private List<ConversationToolDTO> tools = new ArrayList<>();

    /**
     * 扩展参数。
     *
     * <p>用于承载暂未抽象成固定字段的附加上下文，是本次对话请求的兜底扩展容器。</p>
     * <p>适合放入口标识、页面环境、实验开关、附件附加属性等非核心参数。
     * 后端会将该字段合并到内部 command 与下游执行元数据中。</p>
     */
    private Map<String, Object> ext = new HashMap<>();
}

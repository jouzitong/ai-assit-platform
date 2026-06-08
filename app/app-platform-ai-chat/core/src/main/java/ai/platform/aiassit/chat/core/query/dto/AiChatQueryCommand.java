package ai.platform.aiassit.chat.core.query.dto;

import ai.platform.aiassit.chat.history.enums.AiChatBusinessType;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 对话查询内部命令对象。
 *
 * <p>该对象不直接暴露给前端，而是由 controller 在接收外部请求后组装得到，
 * 用于承载 service 执行 AI 对话所需的完整上下文。它的职责是把“前端最小入参”
 * 与“服务端补齐参数”合并为一份稳定的内部执行模型，避免 service 直接依赖 Web 请求 DTO。</p>
 *
 * <p>参数来源大致分为三类：</p>
 * <p>1. 前端直传：如 message、sessionCode、apiModel、attachments、tools、ext。</p>
 * <p>2. controller 补齐：如 userId、traceId、scene、sessionName。</p>
 * <p>3. service 兜底或推导：如 businessType 为空时走默认值、apiModel 为空时走默认模型。</p>
 */
@Data
public class AiChatQueryCommand {

    /**
     * 会话编码。
     *
     * <p>用于标识当前消息归属的对话会话。</p>
     * <p>通常由前端在“继续已有会话”时传入；为空时表示新建会话，由服务端自动生成新的 sessionCode。</p>
     */
    private String sessionCode;

    /**
     * 会话名称。
     *
     * <p>用于展示会话标题，不直接要求前端传入。</p>
     * <p>通常由 controller 基于用户输入的 message 自动截断生成；当命中已有会话时，该值不会覆盖原会话名称。</p>
     */
    private String sessionName;

    /**
     * 当前用户 ID。
     *
     * <p>用于隔离会话、历史消息和轮次数据的归属关系。</p>
     * <p>原则上不允许前端直接控制，通常由 controller 从登录态、请求头或网关透传上下文中提取；
     * 取不到时按现有兼容逻辑回退为 0L。</p>
     */
    private Long userId;

    /**
     * 业务类型。
     *
     * <p>用于标识当前会话所属的业务域，例如通用对话、智能问数等。</p>
     * <p>一般由服务端入口或业务场景决定，不要求前端显式传入；为空时 service 会使用默认业务类型。</p>
     */
    private AiChatBusinessType businessType;

    /**
     * 前端指定的实际模型标识（apiModel）。
     *
     * <p>这是允许前端传入的模型参数，对应模型配置中的 apiModel，而不是内部 modelCode。</p>
     * <p>为空时表示由服务端自动选择默认模型；有值时 service 会直接用于构造 AI 引擎请求，
     * 并基于模型配置反推出 provider 等执行信息。</p>
     */
    private String apiModel;

    /**
     * 用户本次输入的消息内容。
     *
     * <p>这是对话请求的核心业务参数，对应前端输入框中的文本内容。</p>
     * <p>不能为空；service 会将其写入当前轮次的用户消息历史，并作为 AI 引擎的最后一条 USER 消息下发。</p>
     */
    private String message;

    /**
     * 链路追踪 ID。
     *
     * <p>用于串联一次请求在网关、ai-chat、ai-engine 之间的日志和调用链。</p>
     * <p>通常由 controller 优先从请求头提取；如果上游未传，则由服务端生成随机 traceId。</p>
     */
    private String traceId;

    /**
     * 业务场景标识。
     *
     * <p>用于描述当前请求所属的调用场景，例如 ai-chat-query。</p>
     * <p>通常由 controller 固定赋值，不由前端自由指定；最终会透传到 AI 引擎的 RequestMeta.scene。</p>
     */
    private String scene;

    /**
     * 附件列表。
     *
     * <p>用于承载本次对话关联的文件、图片或其他上传资源引用。</p>
     * <p>当前设计建议只传文件引用信息，例如 fileId、fileName、fileUrl、mediaType，
     * 而不直接传输大文件二进制内容。该字段会被并入 ext 后继续透传给下游能力。</p>
     */
    private List<AiChatAttachmentDTO> attachments = new ArrayList<>();

    /**
     * 工具列表。
     *
     * <p>用于声明本次对话可使用或期望使用的工具能力，例如知识库检索、NL2SQL、外部插件等。</p>
     * <p>该字段本身是工具元数据容器，具体是否启用、如何路由、如何鉴权，仍由服务端后续逻辑决定。</p>
     */
    private List<AiChatToolDTO> tools = new ArrayList<>();

    /**
     * 扩展参数。
     *
     * <p>用于承载暂未沉淀为强类型字段的附加上下文，是当前请求的兜底扩展容器。</p>
     * <p>典型内容可以包括入口标识、页面上下文、实验开关、附件补充属性等。
     * service 会在构造 AI 引擎请求时，将该字段与 attachments、tools 合并到 RequestMeta.ext 中。</p>
     */
    private Map<String, Object> ext = new HashMap<>();
}

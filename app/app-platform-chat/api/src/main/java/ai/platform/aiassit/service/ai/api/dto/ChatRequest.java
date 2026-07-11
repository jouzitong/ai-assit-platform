package ai.platform.aiassit.service.ai.api.dto;

import lombok.Data;

import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



@Data
public class ChatRequest implements Serializable {

    /** 指定对话客户端类型，允许为空（由核心路由层自动选择） */
    private AiChatClientType clientType;
    /** 本地模型配置编码；指定时由运行时从模型实体解析客户端和远端模型。 */
    private String modelCode;
    /** 目标模型名称 */
    private String model;
    /** 对话消息列表，通常至少包含一条 user 消息 */
    private List<ChatMessage> messages = new ArrayList<>();
    /** 可选工具定义列表，用于函数调用/工具调用场景 */
    private List<ToolDefinition> tools = new ArrayList<>();
    /** 输出格式约束（纯文本/JSON Schema） */
    private ResponseFormat responseFormat = ResponseFormat.text();
    /** 生成参数（温度、topP、最大 token、超时等） */
    private ChatOptions options = new ChatOptions();
    /** 请求上下文信息（traceId、租户、业务场景） */
    private RequestMeta meta = new RequestMeta();
    /** 平台扩展参数（通用字段无法覆盖时使用） */
    private Map<String, Object> ext = new HashMap<>();
}

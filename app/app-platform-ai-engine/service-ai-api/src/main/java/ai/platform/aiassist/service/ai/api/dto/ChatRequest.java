package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import ai.platform.aiassist.service.ai.api.enums.ProviderType;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;



@Data
public class ChatRequest implements Serializable {

    /** 指定 AI 平台，允许为空（由核心路由层自动选择） */
    private ProviderType provider;
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

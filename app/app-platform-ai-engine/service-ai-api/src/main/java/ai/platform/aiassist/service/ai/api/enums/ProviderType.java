package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum ProviderType implements IEnum {
    OPENAI(1, "OpenAI"),
    DASHSCOPE(2, "通义千问"),
    DEEPSEEK(3, "DeepSeek"),
    OLLAMA(4, "Ollama"),
    CUSTOM(5, "其他"),
    AI_AGENT(6, "AI Agent"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    ProviderType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}

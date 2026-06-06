package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum ProviderType implements IEnum {
    OPENAI(1, "open-ai"),
    DASHSCOPE(2, "千问"),
    DEEPSEEK(3, "Deepseek"),
    OLLAMA(4, ""),
    CUSTOM(5, "其他"),
    ;

    @JsonValue
    private final int code;
    private final String name;

    ProviderType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}

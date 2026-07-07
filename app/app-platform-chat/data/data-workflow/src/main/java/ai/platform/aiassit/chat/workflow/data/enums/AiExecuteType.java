package ai.platform.aiassit.chat.workflow.data.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * AI 执行类型。
 */
@Getter
public enum AiExecuteType implements IEnum {

    CHAT(1, "普通 Chat"),
    AGENT(2, "AI Agent"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiExecuteType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}

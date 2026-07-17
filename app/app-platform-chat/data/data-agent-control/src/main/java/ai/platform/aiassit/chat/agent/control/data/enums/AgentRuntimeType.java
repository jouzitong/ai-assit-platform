package ai.platform.aiassit.chat.agent.control.data.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/** Runtime adapter selected by an enabled entry binding. */
@Getter
public enum AgentRuntimeType implements IEnum {

    OPENAI_AGENTS_PYTHON(1, "OpenAI Agents Python"),
    OPENAI_AGENTS_TYPESCRIPT(2, "OpenAI Agents TypeScript");

    @JsonValue
    private final int code;

    private final String name;

    AgentRuntimeType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}

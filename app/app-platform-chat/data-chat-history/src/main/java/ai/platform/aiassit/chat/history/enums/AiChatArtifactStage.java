package ai.platform.aiassit.chat.history.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 工作流产物所属阶段。
 *
 */
@Getter
public enum AiChatArtifactStage implements IEnum {
    UNDERSTAND(1, "理解阶段"),
    CLARIFY(2, "澄清阶段"),
    PLAN(3, "计划阶段"),
    KNOWLEDGE(4, "知识阶段"),
    SKILL(5, "技能阶段"),
    SQL_GEN(6, "SQL生成阶段"),
    SQL_VALIDATE(7, "SQL验证阶段"),
    SQL_EXEC(8, "SQL执行阶段"),
    RENDER(9, "渲染阶段"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiChatArtifactStage(int code, String name) {
        this.code = code;
        this.name = name;
    }
}

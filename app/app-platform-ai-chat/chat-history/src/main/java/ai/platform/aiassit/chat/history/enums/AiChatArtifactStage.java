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
    UNDERSTAND(1, "UNDERSTAND","理解阶段"),
    CLARIFY(2, "CLARIFY","澄清阶段"),
    PLAN(3, "PLAN","计划阶段"),
    KNOWLEDGE(4, "KNOWLEDGE","知识阶段"),
    SKILL(5, "SKILL","技能阶段"),
    SQL_GEN(6, "SQL_GEN","SQL生成阶段"),
    SQL_VALIDATE(7, "SQL_VALIDATE","SQL验证阶段"),
    SQL_EXEC(8, "SQL_EXEC","SQL执行阶段"),
    RENDER(9, "RENDER","渲染阶段");

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    AiChatArtifactStage(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }

}

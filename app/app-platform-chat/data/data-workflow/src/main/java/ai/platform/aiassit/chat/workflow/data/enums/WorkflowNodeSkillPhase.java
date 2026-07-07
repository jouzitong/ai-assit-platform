package ai.platform.aiassit.chat.workflow.data.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 节点 Skill 挂接阶段。
 *
 * @author zhouzhitong
 * @since 2026/6/15
 */
@Getter
public enum WorkflowNodeSkillPhase implements IEnum {

    BEFORE_EXECUTE(1, "执行前"),
    AFTER_EXECUTE(2, "执行后"),
    REVIEW_OUTPUT(3, "结果审查"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    WorkflowNodeSkillPhase(int code, String name) {
        this.code = code;
        this.name = name;
    }
}

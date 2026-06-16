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

    BEFORE_EXECUTE(1, "BEFORE_EXECUTE", "执行前"),
    AFTER_EXECUTE(2, "AFTER_EXECUTE", "执行后"),
    REVIEW_OUTPUT(3, "REVIEW_OUTPUT", "结果审查"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    WorkflowNodeSkillPhase(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}

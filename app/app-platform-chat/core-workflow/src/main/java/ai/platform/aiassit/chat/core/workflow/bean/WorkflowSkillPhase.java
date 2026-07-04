package ai.platform.aiassit.chat.core.workflow.bean;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 节点技能执行阶段。
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Getter
public enum WorkflowSkillPhase implements IEnum {

    BEFORE_EXECUTE(1, "执行前"),

    AFTER_EXECUTE(2, "执行后"),

    REVIEW_OUTPUT(3, "结果审查"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    WorkflowSkillPhase(int code, String name) {
        this.code = code;
        this.name = name;
    }
}

package ai.platform.aiassit.chat.core.workflow.bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 节点技能配置。
 *
 * @author zhouzhitong
 * @since 2026/6/9
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowNodeSkillConfig implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;

    private WorkflowSkillPhase phase;
}

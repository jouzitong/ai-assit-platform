package ai.platform.aiassit.conversation.workflow.capability;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Prompt 上下文能力执行结果。
 *
 * @author zhouzhitong
 * @since 2026/6/23
 */
@Data
public class PromptContextResult implements Serializable {

    private List<PromptContextItem> items = new ArrayList<>();
}

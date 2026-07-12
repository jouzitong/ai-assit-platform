package ai.platform.aiassit.conversation.workflow.capability;

import lombok.Data;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Prompt 上下文片段。
 *
 * @author zhouzhitong
 * @since 2026/6/23
 */
@Data
public class PromptContextItem implements Serializable {

    private String title;

    private String source;

    private String content;

    private Integer priority = 100;

    private Map<String, Object> metadata = new LinkedHashMap<>();
}

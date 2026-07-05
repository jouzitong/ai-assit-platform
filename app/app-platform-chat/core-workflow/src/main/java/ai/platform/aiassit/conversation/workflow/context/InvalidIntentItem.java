package ai.platform.aiassit.conversation.workflow.context;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 已失效意图项。
 *
 * @author zhouzhitong
 * @since 2026/6/22
 */
@Data
public class InvalidIntentItem implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 已失效意图内容。
     */
    private String content;

    /**
     * 失效判断依据说明。
     */
    private String reason;

    /**
     * 失效判断证据。
     */
    private String evidence;
}

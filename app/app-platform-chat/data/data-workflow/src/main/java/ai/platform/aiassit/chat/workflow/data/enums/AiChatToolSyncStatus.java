package ai.platform.aiassit.chat.workflow.data.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * Tool 同步状态。
 *
 * @author zhouzhitong
 * @since 2026/7/7
 */
@Getter
public enum AiChatToolSyncStatus implements IEnum {

    PENDING(1, "待同步"),
    SUCCESS(2, "同步成功"),
    FAILED(3, "同步失败"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiChatToolSyncStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }
}

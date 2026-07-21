package ai.platform.aiassit.conversation.data.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 会话非文本产物类型。
 *
 * <p>文本、Markdown 和工具文本结果由会话消息承载；这里只保留需要独立展示或引用的产物。</p>
 */
@Getter
public enum ConversationArtifactType implements IEnum {
    FILE(1, "文件"),
    IMAGE(2, "图片"),
    RENDER_JSON(3, "Render JSON"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    ConversationArtifactType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}

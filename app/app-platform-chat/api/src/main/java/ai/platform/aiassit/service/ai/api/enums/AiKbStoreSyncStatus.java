package ai.platform.aiassit.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbStoreSyncStatus implements IEnum {
    CREATING(1, "创建中"),
    ACTIVE(2, "已同步"),
    CREATE_FAILED(3, "创建失败"),
    UPDATING(4, "更新中"),
    UPDATE_FAILED(5, "更新失败"),
    DELETE_PENDING(6, "删除中"),
    DELETE_FAILED(7, "删除失败"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiKbStoreSyncStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }
}

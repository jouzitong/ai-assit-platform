package ai.platform.aiassit.db.engine.meta.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 数据源同步模式。
 */
@Getter
public enum DbDataSourceSyncMode implements IEnum {
    NONE(0, "无需同步"),
    REALTIME(1, "实时"),
    MINUTE_LEVEL(2, "分钟级"),
    HOURLY(3, "小时级"),
    T_PLUS_1(4, "T+1"),
    MANUAL(5, "手动"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    DbDataSourceSyncMode(int code, String name) {
        this.code = code;
        this.name = name;
    }
}

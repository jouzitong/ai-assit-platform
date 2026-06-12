package ai.platform.aiassit.db.engine.meta.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 数据源同步模式。
 */
@Getter
public enum DbDataSourceSyncMode implements IEnum {
    REALTIME(1, "REALTIME", "实时"),
    MINUTE_LEVEL(2, "MINUTE_LEVEL", "分钟级"),
    HOURLY(3, "HOURLY", "小时级"),
    T_PLUS_1(4, "T_PLUS_1", "T+1"),
    MANUAL(5, "MANUAL", "手动"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    DbDataSourceSyncMode(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}

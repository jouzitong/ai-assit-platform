package ai.platform.aiassit.db.engine.meta.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 数据接入源类型。
 */
@Getter
public enum DbDataSourceType implements IEnum {
    DATABASE(1, "数据库"),
    HTTP_API(2, "HTTP API"),
    SERVICE_API(3, "服务接口"),
    FILE(4, "文件"),
    STREAM(5, "流式数据"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    DbDataSourceType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}

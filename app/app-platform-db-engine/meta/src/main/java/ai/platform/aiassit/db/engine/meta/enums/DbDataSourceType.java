package ai.platform.aiassit.db.engine.meta.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/**
 * 数据接入源类型。
 */
@Getter
public enum DbDataSourceType implements IEnum {
    DATABASE(1, "DATABASE", "数据库"),
    HTTP_API(2, "HTTP_API", "HTTP API"),
    SERVICE_API(3, "SERVICE_API", "服务接口"),
    FILE(4, "FILE", "文件"),
    STREAM(5, "STREAM", "流式数据"),
    ;

    private final int code;

    @JsonValue
    private final String name;

    private final String desc;

    DbDataSourceType(int code, String name, String desc) {
        this.code = code;
        this.name = name;
        this.desc = desc;
    }
}

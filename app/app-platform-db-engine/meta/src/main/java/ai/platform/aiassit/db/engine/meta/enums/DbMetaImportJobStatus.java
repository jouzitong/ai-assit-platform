package ai.platform.aiassit.db.engine.meta.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum DbMetaImportJobStatus implements IEnum {

    PENDING(1, "待执行"),
    RUNNING(2, "执行中"),
    COMPLETED(3, "已完成"),
    FAILED(4, "失败"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    DbMetaImportJobStatus(int code, String name) {
        this.code = code;
        this.name = name;
    }
}

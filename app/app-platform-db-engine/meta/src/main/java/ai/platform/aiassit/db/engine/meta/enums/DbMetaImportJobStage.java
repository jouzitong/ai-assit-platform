package ai.platform.aiassit.db.engine.meta.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum DbMetaImportJobStage implements IEnum {

    QUEUED(1, "排队中"),
    PARSING(2, "解析中"),
    IMPORTING_TABLES(3, "导入表"),
    IMPORTING_FIELDS(4, "导入字段"),
    IMPORTING_INDEXES(5, "导入索引"),
    FINALIZING(6, "收尾处理"),
    COMPLETED(7, "已完成"),
    FAILED(8, "失败"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    DbMetaImportJobStage(int code, String name) {
        this.code = code;
        this.name = name;
    }
}

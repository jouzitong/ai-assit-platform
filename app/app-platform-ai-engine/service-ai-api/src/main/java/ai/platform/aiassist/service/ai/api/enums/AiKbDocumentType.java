package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbDocumentType implements IEnum {
    DB_TABLE(1, "数据库表文档", AiKbSourceType.DB_DATA_SOURCE),
    ;

    @JsonValue
    private final int code;

    private final String name;

    /** 该文档类型允许归属的来源类型。 */
    private final AiKbSourceType sourceType;

    AiKbDocumentType(int code, String name, AiKbSourceType sourceType) {
        this.code = code;
        this.name = name;
        this.sourceType = sourceType;
    }
}

package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbDocumentType implements IEnum {
    DB_TABLE(1, "数据库表文档", AiKbBizType.DB_DATA_SOURCE),
    ;

    @JsonValue
    private final int code;

    private final String name;

    /** 该文档类型默认归属的业务类型。 */
    private final AiKbBizType bizType;

    AiKbDocumentType(int code, String name, AiKbBizType bizType) {
        this.code = code;
        this.name = name;
        this.bizType = bizType;
    }
}

package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbBizType implements IEnum {
    DB_DATA_SOURCE(1, "数据库数据源"),
    BUSINESS_ANALYSIS_SCENE(2, "业务分析场景"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiKbBizType(int code, String name) {
        this.code = code;
        this.name = name;
    }
}

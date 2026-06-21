package ai.platform.aiassist.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

@Getter
public enum AiKbPublishStage implements IEnum {
    PREPARE_VERSION(1, "准备版本"),
    VALIDATE_DOCUMENTS(2, "校验文档"),
    GENERATE_DIFF(3, "生成差异"),
    UPSERT_AI_DOCUMENTS(4, "写入 AI 文档"),
    DELETE_AI_DOCUMENTS(5, "删除 AI 文档"),
    FINALIZE(6, "完成发布"),
    COMPLETED(7, "已完成"),
    FAILED(8, "失败"),
    ;

    @JsonValue
    private final int code;

    private final String name;

    AiKbPublishStage(int code, String name) {
        this.code = code;
        this.name = name;
    }
}

package ai.platform.aiassit.service.ai.api.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;
import org.arthena.framework.common.enums.IEnum;

/** 知识库 Provider 认证方式。 */
@Getter
public enum AiKbAuthType implements IEnum {

    /** API Key 通过 Bearer 请求头传递。 */
    BEARER(1, "Bearer"),
    /** 阿里云 AccessKey ID/Secret 请求签名。 */
    ALIYUN_AKSK(2, "阿里云AK/SK");

    @JsonValue
    private final int code;

    private final String name;

    AiKbAuthType(int code, String name) {
        this.code = code;
        this.name = name;
    }

    @JsonCreator
    public static AiKbAuthType fromCode(int code) {
        for (AiKbAuthType value : values()) {
            if (value.code == code) {
                return value;
            }
        }
        throw new IllegalArgumentException("unsupported kb auth type: " + code);
    }
}

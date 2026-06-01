package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import ai.platform.aiassist.service.ai.api.enums.ResponseFormatType;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;



@Data
public class ResponseFormat implements Serializable {

    /** 输出格式类型（TEXT/JSON_SCHEMA） */
    private ResponseFormatType type;
    /** 当 type=JSON_SCHEMA 时的结构定义 */
    private Map<String, Object> schema = new HashMap<>();

    public static ResponseFormat text() {
        ResponseFormat format = new ResponseFormat();
        format.setType(ResponseFormatType.TEXT);
        return format;
    }
}

package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DbQueryRelation {

    /**
     * 关联 SQL 的唯一标识。
     */
    private String key;

    private String model;

    private String type;

    private Map<String, String> on = new LinkedHashMap<>();

    /**
     * 关联模型过滤条件。
     *
     * <p>用于限制关联模型自身的数据范围，例如只关联有效状态、指定类型、指定时间范围内的数据。</p>
     */
    private Map<String, Object> filter = new LinkedHashMap<>();
}

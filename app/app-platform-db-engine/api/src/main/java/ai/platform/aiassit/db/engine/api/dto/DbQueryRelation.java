package ai.platform.aiassit.db.engine.api.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

@Data
public class DbQueryRelation {

    /**
     * 本次查询中的关联别名，同时作为返回结果的 key。
     */
    private String key;

    /**
     * 目标虚拟表名。
     */
    private String model;

    /**
     * 关联类型，当前虚拟查询兼容层仅支持 left。
     */
    private String type;

    /**
     * 当前主虚拟表字段到目标虚拟表字段的关联映射。
     *
     * <p>已发布关系可省略；未配置关系时必须提供。</p>
     */
    private Map<String, String> on = new LinkedHashMap<>();

    /**
     * 关联模型过滤条件。
     *
     * <p>用于限制关联模型自身的数据范围，例如只关联有效状态、指定类型、指定时间范围内的数据。</p>
     */
    private Map<String, Object> filter = new LinkedHashMap<>();
}

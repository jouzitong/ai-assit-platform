package ai.platform.aiassit.data.virtualization.api.dto;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.RelationResultMode;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** 单次查询中的关系声明，filter 保持 JOIN ON 作用域。 */
@Data
public class VirtualRelationRequest {

    /** 本次查询中的关系别名，同时作为返回字段的 key。 */
    private String key;

    /** 已发布关系编码；临时关系为 null。 */
    private String relationCode;

    /** 目标虚拟实体编码。 */
    private String targetEntityCode;

    /** 当前虚拟实体字段到目标虚拟实体字段的映射。 */
    private Map<String, String> localToRemoteFields = new LinkedHashMap<>();

    /** 关系从当前查询方向返回时的结果形态。 */
    private RelationResultMode resultMode;

    private FilterNode filter;
}

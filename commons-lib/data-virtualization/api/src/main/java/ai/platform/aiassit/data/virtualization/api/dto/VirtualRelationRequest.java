package ai.platform.aiassit.data.virtualization.api.dto;

import lombok.Data;

/** 单次查询中的关系声明，filter 保持 JOIN ON 作用域。 */
@Data
public class VirtualRelationRequest {

    private String relationCode;

    private FilterNode filter;
}

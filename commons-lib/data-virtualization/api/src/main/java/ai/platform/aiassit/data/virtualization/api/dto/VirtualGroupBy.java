package ai.platform.aiassit.data.virtualization.api.dto;

import lombok.Data;

/** 带稳定输出别名的分组字段。 */
@Data
public class VirtualGroupBy {

    private String field;

    private String alias;
}

package ai.platform.aiassit.data.virtualization.api.dto;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.AggregateFunction;
import lombok.Data;

@Data
public class VirtualAggregate {
    private String field;
    private AggregateFunction function;
    private String alias;
}

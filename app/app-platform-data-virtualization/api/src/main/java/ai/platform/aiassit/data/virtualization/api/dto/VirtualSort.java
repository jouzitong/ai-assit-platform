package ai.platform.aiassit.data.virtualization.api.dto;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.SortDirection;
import lombok.Data;

@Data
public class VirtualSort {
    private String field;
    private SortDirection direction = SortDirection.ASC;
}

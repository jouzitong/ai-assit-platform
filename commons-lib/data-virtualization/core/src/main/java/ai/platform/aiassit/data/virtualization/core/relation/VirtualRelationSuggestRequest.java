package ai.platform.aiassit.data.virtualization.core.relation;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VirtualRelationSuggestRequest {
    private List<Long> entityIds = new ArrayList<>();
}

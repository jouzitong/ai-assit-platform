package ai.platform.aiassit.data.virtualization.core.knowledge;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VirtualKnowledgeBatchRequest {
    private List<Long> entityIds = new ArrayList<>();
}

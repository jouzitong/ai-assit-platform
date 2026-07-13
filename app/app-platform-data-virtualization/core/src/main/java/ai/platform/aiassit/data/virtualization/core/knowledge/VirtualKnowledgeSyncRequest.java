package ai.platform.aiassit.data.virtualization.core.knowledge;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class VirtualKnowledgeSyncRequest {
    private String kbCode;
    private List<Long> entityIds = new ArrayList<>();
}

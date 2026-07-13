package ai.platform.aiassit.data.virtualization.core.knowledge;

import java.util.List;

public record VirtualKnowledgeStatusItem(Long entityId, List<String> kbCodes) {
    public VirtualKnowledgeStatusItem {
        kbCodes = kbCodes == null ? List.of() : List.copyOf(kbCodes);
    }
}

package ai.platform.aiassit.data.virtualization.core.relation;

import ai.platform.aiassit.data.virtualization.data.dto.VirtualRelationDTO;

public record VirtualRelationSuggestion(VirtualRelationDTO relation, String reason, double confidence) {
}

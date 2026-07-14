package ai.platform.aiassit.data.virtualization.spi.knowledge;

import java.util.List;

public record KnowledgeDocumentDeleteResult(int deletedCount, List<String> skippedDocumentCodes) {
    public KnowledgeDocumentDeleteResult {
        skippedDocumentCodes = skippedDocumentCodes == null ? List.of() : List.copyOf(skippedDocumentCodes);
    }
}

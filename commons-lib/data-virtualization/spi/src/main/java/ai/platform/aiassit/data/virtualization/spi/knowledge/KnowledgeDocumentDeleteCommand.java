package ai.platform.aiassit.data.virtualization.spi.knowledge;

import java.util.List;

public record KnowledgeDocumentDeleteCommand(List<String> documentCodes) {
    public KnowledgeDocumentDeleteCommand {
        documentCodes = documentCodes == null ? List.of() : List.copyOf(documentCodes);
    }
}

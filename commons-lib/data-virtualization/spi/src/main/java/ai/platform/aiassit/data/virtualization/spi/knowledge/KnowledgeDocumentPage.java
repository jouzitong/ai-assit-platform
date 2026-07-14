package ai.platform.aiassit.data.virtualization.spi.knowledge;

import java.util.List;

public record KnowledgeDocumentPage(List<KnowledgeDocumentRef> documents) {
    public KnowledgeDocumentPage {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }
}

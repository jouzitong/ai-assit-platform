package ai.platform.aiassit.data.virtualization.spi.knowledge;

public interface KnowledgeDocumentPort {

    KnowledgeDocumentPage list(KnowledgeDocumentQuery query);

    KnowledgeDocumentUpsertResult upsert(KnowledgeDocumentCommand command);

    KnowledgeDocumentDeleteResult delete(KnowledgeDocumentDeleteCommand command);
}

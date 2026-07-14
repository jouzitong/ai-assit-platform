package ai.platform.aiassit.db.engine.virtualization.adapter.external;

import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentCommand;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentDeleteCommand;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentDeleteResult;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentPage;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentPort;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentQuery;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentRef;
import ai.platform.aiassit.data.virtualization.spi.knowledge.KnowledgeDocumentUpsertResult;
import ai.platform.aiassit.service.ai.api.AiKnowledgeApi;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentBatchRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentDeleteResponse;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentListItemDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassit.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassit.service.ai.api.enums.AiKbDocumentType;
import org.athena.framework.web.vo.R;
import org.springframework.stereotype.Component;

import java.util.List;

/** Keeps Chat knowledge-base HTTP DTOs outside the virtualization core. */
@Component
public class AiKnowledgeDocumentAdapter implements KnowledgeDocumentPort {

    private final AiKnowledgeApi knowledgeApi;

    public AiKnowledgeDocumentAdapter(AiKnowledgeApi knowledgeApi) {
        this.knowledgeApi = knowledgeApi;
    }

    @Override
    public KnowledgeDocumentPage list(KnowledgeDocumentQuery query) {
        List<String> documentCodes = query == null ? List.of() : query.documentCodes();
        if (documentCodes == null || documentCodes.isEmpty()) {
            return new KnowledgeDocumentPage(List.of());
        }
        AiKbDocumentBatchRequest request = new AiKbDocumentBatchRequest();
        request.setDocumentCodes(documentCodes);
        List<AiKbDocumentListItemDTO> data = requiredData(
                knowledgeApi.listDocuments(request), "查询知识库文档失败");
        List<KnowledgeDocumentRef> documents = data.stream()
                .filter(item -> item != null)
                .map(item -> new KnowledgeDocumentRef(item.getDocumentCode(), item.getKbCode()))
                .toList();
        return new KnowledgeDocumentPage(documents);
    }

    @Override
    public KnowledgeDocumentUpsertResult upsert(KnowledgeDocumentCommand command) {
        if (command == null || !hasText(command.knowledgeBaseCode()) || !hasText(command.documentCode())) {
            throw new IllegalArgumentException("knowledge base code 和 document code 不能为空");
        }
        AiKbDocumentUpsertRequest request = new AiKbDocumentUpsertRequest();
        request.setKbId(command.knowledgeBaseCode().trim());
        request.setDocumentId(command.documentCode().trim());
        request.setDocumentName(command.documentName());
        request.setDocumentType(AiKbDocumentType.DB_TABLE);
        request.setBizType(AiKbBizType.DB_DATA_SOURCE);
        request.setContent(command.content());
        request.setCanUpdate(command.updateAllowed());
        request.setExt(command.metadata());
        AiKbDocumentUpsertResponse data = requiredData(
                knowledgeApi.upsertDocument(request), "同步知识库文档失败");
        return new KnowledgeDocumentUpsertResult(
                Boolean.TRUE.equals(data.getCreated()),
                Boolean.TRUE.equals(data.getUpdated())
        );
    }

    @Override
    public KnowledgeDocumentDeleteResult delete(KnowledgeDocumentDeleteCommand command) {
        List<String> documentCodes = command == null ? List.of() : command.documentCodes();
        if (documentCodes == null || documentCodes.isEmpty()) {
            return new KnowledgeDocumentDeleteResult(0, List.of());
        }
        AiKbDocumentBatchRequest request = new AiKbDocumentBatchRequest();
        request.setDocumentCodes(documentCodes);
        AiKbDocumentDeleteResponse data = requiredData(
                knowledgeApi.deleteDocuments(request), "删除知识库文档失败");
        return new KnowledgeDocumentDeleteResult(
                data.getDeletedCount() == null ? 0 : data.getDeletedCount(),
                data.getSkippedDocumentCodes()
        );
    }

    private <T> T requiredData(R<T> response, String operation) {
        if (response == null) {
            throw new IllegalStateException(operation + ": 无响应");
        }
        if (!response.isOk()) {
            throw new IllegalStateException(operation + ", code=" + response.getCode());
        }
        if (response.getData() == null) {
            throw new IllegalStateException(operation + ": 响应数据为空");
        }
        return response.getData();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

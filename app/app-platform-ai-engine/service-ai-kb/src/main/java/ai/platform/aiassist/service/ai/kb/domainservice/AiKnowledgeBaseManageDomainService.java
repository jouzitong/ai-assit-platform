package ai.platform.aiassist.service.ai.kb.domainservice;

import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.AiKbDocumentUpsertResponse;
import ai.platform.aiassist.service.ai.kb.controller.req.AiKbSyncRequest;
import ai.platform.aiassist.service.ai.kb.controller.resp.AiKbSyncResponse;

public interface AiKnowledgeBaseManageDomainService {

    AiKbDocumentUpsertResponse upsertDocument(AiKbDocumentUpsertRequest request);

    AiKbSyncResponse syncDocument(AiKbSyncRequest request);
}

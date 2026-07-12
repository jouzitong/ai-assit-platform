package ai.platform.aiassit.knowledge.manage.domainservice.impl;

import ai.platform.aiassit.knowledge.manage.domainservice.AiKnowledgeDatasetService;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetListRequest;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import ai.platform.aiassit.service.ai.spi.KnowledgeDatasetService;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** {@link AiKnowledgeDatasetService} 默认实现。 */
@Service
public class AiKnowledgeDatasetServiceImpl implements AiKnowledgeDatasetService {

    private final Map<AiKnowledgeClientType, KnowledgeDatasetService> datasetServices;

    public AiKnowledgeDatasetServiceImpl(List<KnowledgeDatasetService> datasetServices) {
        this.datasetServices = datasetServices.stream().collect(Collectors.toMap(
                KnowledgeDatasetService::knowledgeClientType,
                Function.identity(),
                (left, right) -> {
                    throw new IllegalStateException("duplicate knowledge dataset service: "
                            + left.knowledgeClientType());
                }
        ));
    }

    @Override
    public List<AiKbDatasetDTO> listDatasets(AiKbDatasetListRequest request) {
        AiKbDatasetListRequest normalized = request == null ? new AiKbDatasetListRequest() : request;
        AiKnowledgeClientType clientType = normalized.getClientType() == null
                ? AiKnowledgeClientType.RAGFLOW : normalized.getClientType();
        KnowledgeDatasetService datasetService = datasetServices.get(clientType);
        if (datasetService == null) {
            throw BizException.of(AiChatBizCodeConstant.KNOWLEDGE_SERVICE_NOT_FOUND,
                    "knowledge dataset service: " + clientType);
        }
        return datasetService.listDatasets(normalized);
    }
}

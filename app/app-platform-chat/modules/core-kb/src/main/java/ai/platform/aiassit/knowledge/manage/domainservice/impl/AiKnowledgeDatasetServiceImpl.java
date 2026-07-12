package ai.platform.aiassit.knowledge.manage.domainservice.impl;

import ai.platform.aiassit.knowledge.manage.domainservice.AiKnowledgeDatasetService;
import ai.platform.aiassit.execution.service.KnowledgeClientConfigService;
import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetSaveRequest;
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
    private final KnowledgeClientConfigService knowledgeClientConfigService;

    public AiKnowledgeDatasetServiceImpl(List<KnowledgeDatasetService> datasetServices,
                                         KnowledgeClientConfigService knowledgeClientConfigService) {
        this.datasetServices = datasetServices.stream().collect(Collectors.toMap(
                KnowledgeDatasetService::knowledgeClientType,
                Function.identity(),
                (left, right) -> {
                    throw new IllegalStateException("duplicate knowledge dataset service: "
                            + left.knowledgeClientType());
                }
        ));
        this.knowledgeClientConfigService = knowledgeClientConfigService;
    }

    @Override
    public List<AiKbDatasetDTO> listDatasets(AiKbDatasetListRequest request) {
        AiKbDatasetListRequest normalized = request == null ? new AiKbDatasetListRequest() : request;
        return requireDatasetService(normalized.getClientType()).listDatasets(normalized);
    }

    /**
     * 使用系统参数中选定的客户端查询 Provider Dataset；认证信息不会离开服务端。
     */
    @Override
    public List<AiKbDatasetDTO> listDatasets(String clientKey, AiKbDatasetListRequest request) {
        AiKbDatasetListRequest normalized = request == null ? new AiKbDatasetListRequest() : request;
        AiKnowledgeClientType clientType = knowledgeClientConfigService.requireOption(clientKey).getClientType();
        normalized.setClientType(clientType);
        normalized.setMeta(knowledgeClientConfigService.apply(clientKey, clientType, normalized.getMeta()));
        return listDatasets(normalized);
    }

    @Override
    public AiKbDatasetDTO createDataset(AiKbDatasetSaveRequest request) {
        AiKbDatasetSaveRequest normalized = request == null ? new AiKbDatasetSaveRequest() : request;
        return requireDatasetService(normalized.getClientType()).createDataset(normalized);
    }

    @Override
    public AiKbDatasetDTO createDataset(String clientKey, AiKbDatasetSaveRequest request) {
        AiKbDatasetSaveRequest normalized = request == null ? new AiKbDatasetSaveRequest() : request;
        AiKnowledgeClientType clientType = knowledgeClientConfigService.requireOption(clientKey).getClientType();
        normalized.setClientType(clientType);
        normalized.setMeta(knowledgeClientConfigService.apply(clientKey, clientType, normalized.getMeta()));
        return createDataset(normalized);
    }

    @Override
    public AiKbDatasetDTO updateDataset(String kbId, AiKbDatasetSaveRequest request) {
        AiKbDatasetSaveRequest normalized = request == null ? new AiKbDatasetSaveRequest() : request;
        return requireDatasetService(normalized.getClientType()).updateDataset(kbId, normalized);
    }

    @Override
    public AiKbDatasetDTO updateDataset(String clientKey, String kbId, AiKbDatasetSaveRequest request) {
        AiKbDatasetSaveRequest normalized = request == null ? new AiKbDatasetSaveRequest() : request;
        AiKnowledgeClientType clientType = knowledgeClientConfigService.requireOption(clientKey).getClientType();
        normalized.setClientType(clientType);
        normalized.setMeta(knowledgeClientConfigService.apply(clientKey, clientType, normalized.getMeta()));
        return updateDataset(kbId, normalized);
    }

    @Override
    public int deleteDatasets(AiKbDatasetDeleteRequest request) {
        AiKbDatasetDeleteRequest normalized = request == null ? new AiKbDatasetDeleteRequest() : request;
        return requireDatasetService(normalized.getClientType()).deleteDatasets(normalized);
    }

    @Override
    public int deleteDatasets(String clientKey, AiKbDatasetDeleteRequest request) {
        AiKbDatasetDeleteRequest normalized = request == null ? new AiKbDatasetDeleteRequest() : request;
        AiKnowledgeClientType clientType = knowledgeClientConfigService.requireOption(clientKey).getClientType();
        normalized.setClientType(clientType);
        normalized.setMeta(knowledgeClientConfigService.apply(clientKey, clientType, normalized.getMeta()));
        return deleteDatasets(normalized);
    }

    private KnowledgeDatasetService requireDatasetService(AiKnowledgeClientType clientType) {
        AiKnowledgeClientType normalizedClientType = clientType == null ? AiKnowledgeClientType.RAGFLOW : clientType;
        KnowledgeDatasetService datasetService = datasetServices.get(normalizedClientType);
        if (datasetService == null) {
            throw BizException.of(AiChatBizCodeConstant.KNOWLEDGE_SERVICE_NOT_FOUND,
                    "knowledge dataset service: " + normalizedClientType);
        }
        return datasetService;
    }
}

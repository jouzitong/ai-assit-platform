package ai.platform.aiassit.service.ai.provider.service;

import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDTO;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetDeleteRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetListRequest;
import ai.platform.aiassit.service.ai.api.dto.AiKbDatasetSaveRequest;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import ai.platform.aiassit.service.ai.provider.client.RagflowKnowledgeBaseClient;
import ai.platform.aiassit.service.ai.spi.KnowledgeDatasetService;
import org.arthena.framework.common.exception.BizException;
import org.springframework.stereotype.Component;

import java.util.List;

/** RAGFlow Dataset 管理 SPI 实现。 */
@Component
//@ConditionalOnProperty(prefix = "ai.provider.ragflow", name = "enabled", havingValue = "true")
public class RagflowDatasetService implements KnowledgeDatasetService {

    private final RagflowKnowledgeBaseClient knowledgeBaseClient;

    public RagflowDatasetService(RagflowKnowledgeBaseClient knowledgeBaseClient) {
        this.knowledgeBaseClient = knowledgeBaseClient;
    }

    @Override
    public AiKnowledgeClientType knowledgeClientType() {
        return AiKnowledgeClientType.RAGFLOW;
    }

    @Override
    public List<AiKbDatasetDTO> listDatasets(AiKbDatasetListRequest request) {
        try {
            return knowledgeBaseClient.listDatasets(request);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, ex.getMessage());
        }
    }

    @Override
    public AiKbDatasetDTO createDataset(AiKbDatasetSaveRequest request) {
        try {
            return knowledgeBaseClient.createDataset(request);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, ex.getMessage());
        }
    }

    @Override
    public AiKbDatasetDTO updateDataset(String kbId, AiKbDatasetSaveRequest request) {
        try {
            return knowledgeBaseClient.updateDataset(kbId, request);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, ex.getMessage());
        }
    }

    @Override
    public int deleteDatasets(AiKbDatasetDeleteRequest request) {
        try {
            return knowledgeBaseClient.deleteDatasets(request);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw BizException.of(AiChatBizCodeConstant.PROVIDER_PROCESS_FAILED, ex.getMessage());
        }
    }
}

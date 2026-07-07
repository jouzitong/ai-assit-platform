package ai.platform.aiassit.chat.workflow.data.service.impl;

import ai.platform.aiassit.chat.workflow.data.convert.AiChatWorkflowConfigNodeConvert;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowConfigNodeEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatWorkflowConfigNodeDTO;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatWorkflowConfigNodeMapper;
import ai.platform.aiassit.chat.workflow.data.service.AiChatWorkflowConfigNodeService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class AiChatWorkflowConfigNodeServiceImpl
        extends BaseMapperService<AiChatWorkflowConfigNodeEntity, AiChatWorkflowConfigNodeMapper, AiChatWorkflowConfigNodeDTO>
        implements AiChatWorkflowConfigNodeService {

    private final AiChatWorkflowConfigNodeConvert convert;

    public AiChatWorkflowConfigNodeServiceImpl(AiChatWorkflowConfigNodeConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<AiChatWorkflowConfigNodeEntity, AiChatWorkflowConfigNodeDTO> convert() {
        return convert;
    }
}

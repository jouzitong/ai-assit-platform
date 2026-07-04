package ai.platform.aiassit.chat.workflow.data.service.impl;

import ai.platform.aiassit.chat.workflow.data.convert.AiChatWorkflowConfigConvert;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowConfigEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatWorkflowConfigDTO;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatWorkflowConfigMapper;
import ai.platform.aiassit.chat.workflow.data.service.AiChatWorkflowConfigService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class AiChatWorkflowConfigServiceImpl
        extends BaseMapperService<AiChatWorkflowConfigEntity, AiChatWorkflowConfigMapper, AiChatWorkflowConfigDTO>
        implements AiChatWorkflowConfigService {

    private final AiChatWorkflowConfigConvert convert;

    public AiChatWorkflowConfigServiceImpl(AiChatWorkflowConfigConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<AiChatWorkflowConfigEntity, AiChatWorkflowConfigDTO> convert() {
        return convert;
    }
}

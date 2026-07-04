package ai.platform.aiassit.chat.workflow.data.service.impl;

import ai.platform.aiassit.chat.workflow.data.convert.AiChatWorkflowConvert;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatWorkflowDTO;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatWorkflowMapper;
import ai.platform.aiassit.chat.workflow.data.service.AiChatWorkflowService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class AiChatWorkflowServiceImpl
        extends BaseMapperService<AiChatWorkflowEntity, AiChatWorkflowMapper, AiChatWorkflowDTO>
        implements AiChatWorkflowService {

    private final AiChatWorkflowConvert convert;

    public AiChatWorkflowServiceImpl(AiChatWorkflowConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<AiChatWorkflowEntity, AiChatWorkflowDTO> convert() {
        return convert;
    }
}

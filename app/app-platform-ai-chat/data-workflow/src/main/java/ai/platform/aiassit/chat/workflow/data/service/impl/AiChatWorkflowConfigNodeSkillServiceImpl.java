package ai.platform.aiassit.chat.workflow.data.service.impl;

import ai.platform.aiassit.chat.workflow.data.convert.AiChatWorkflowConfigNodeSkillConvert;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatWorkflowConfigNodeSkillEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatWorkflowConfigNodeSkillDTO;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatWorkflowConfigNodeSkillMapper;
import ai.platform.aiassit.chat.workflow.data.service.AiChatWorkflowConfigNodeSkillService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class AiChatWorkflowConfigNodeSkillServiceImpl
        extends BaseMapperService<AiChatWorkflowConfigNodeSkillEntity, AiChatWorkflowConfigNodeSkillMapper, AiChatWorkflowConfigNodeSkillDTO>
        implements AiChatWorkflowConfigNodeSkillService {

    private final AiChatWorkflowConfigNodeSkillConvert convert;

    public AiChatWorkflowConfigNodeSkillServiceImpl(AiChatWorkflowConfigNodeSkillConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<AiChatWorkflowConfigNodeSkillEntity, AiChatWorkflowConfigNodeSkillDTO> convert() {
        return convert;
    }
}

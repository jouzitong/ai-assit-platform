package ai.platform.aiassit.chat.workflow.data.service.impl;

import ai.platform.aiassit.chat.workflow.data.convert.AiChatSkillConvert;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatSkillEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatSkillDTO;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatSkillMapper;
import ai.platform.aiassit.chat.workflow.data.service.AiChatSkillService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class AiChatSkillServiceImpl
        extends BaseMapperService<AiChatSkillEntity, AiChatSkillMapper, AiChatSkillDTO>
        implements AiChatSkillService {

    private final AiChatSkillConvert convert;

    public AiChatSkillServiceImpl(AiChatSkillConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<AiChatSkillEntity, AiChatSkillDTO> convert() {
        return convert;
    }
}

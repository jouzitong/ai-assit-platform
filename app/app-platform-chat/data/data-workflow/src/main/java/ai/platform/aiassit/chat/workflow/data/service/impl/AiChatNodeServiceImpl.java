package ai.platform.aiassit.chat.workflow.data.service.impl;

import ai.platform.aiassit.chat.workflow.data.convert.AiChatNodeConvert;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatNodeEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatNodeDTO;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatNodeMapper;
import ai.platform.aiassit.chat.workflow.data.service.AiChatNodeService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class AiChatNodeServiceImpl
        extends BaseMapperService<AiChatNodeEntity, AiChatNodeMapper, AiChatNodeDTO>
        implements AiChatNodeService {

    private final AiChatNodeConvert convert;

    public AiChatNodeServiceImpl(AiChatNodeConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<AiChatNodeEntity, AiChatNodeDTO> convert() {
        return convert;
    }
}

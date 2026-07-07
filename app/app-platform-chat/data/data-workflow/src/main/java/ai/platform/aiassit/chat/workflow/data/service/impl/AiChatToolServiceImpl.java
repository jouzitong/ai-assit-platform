package ai.platform.aiassit.chat.workflow.data.service.impl;

import ai.platform.aiassit.chat.workflow.data.convert.AiChatToolConvert;
import ai.platform.aiassit.chat.workflow.data.entity.AiChatToolEntity;
import ai.platform.aiassit.chat.workflow.data.entity.dto.AiChatToolDTO;
import ai.platform.aiassit.chat.workflow.data.mapper.AiChatToolMapper;
import ai.platform.aiassit.chat.workflow.data.service.AiChatToolService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class AiChatToolServiceImpl
        extends BaseMapperService<AiChatToolEntity, AiChatToolMapper, AiChatToolDTO>
        implements AiChatToolService {

    private final AiChatToolConvert convert;

    public AiChatToolServiceImpl(AiChatToolConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<AiChatToolEntity, AiChatToolDTO> convert() {
        return convert;
    }
}

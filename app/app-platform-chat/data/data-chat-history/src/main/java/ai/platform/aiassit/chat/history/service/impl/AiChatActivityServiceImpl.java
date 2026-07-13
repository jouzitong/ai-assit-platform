package ai.platform.aiassit.chat.history.service.impl;

import ai.platform.aiassit.chat.history.convert.AiChatActivityConvert;
import ai.platform.aiassit.chat.history.entity.AiChatActivityEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatActivityDTO;
import ai.platform.aiassit.chat.history.mapper.AiChatActivityMapper;
import ai.platform.aiassit.chat.history.service.AiChatActivityService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class AiChatActivityServiceImpl
        extends BaseMapperService<AiChatActivityEntity, AiChatActivityMapper, AiChatActivityDTO>
        implements AiChatActivityService {

    private final AiChatActivityConvert activityConvert;

    public AiChatActivityServiceImpl(AiChatActivityConvert activityConvert) {
        this.activityConvert = activityConvert;
    }

    @Override
    protected IConvert<AiChatActivityEntity, AiChatActivityDTO> convert() {
        return activityConvert;
    }
}

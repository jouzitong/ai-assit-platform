package ai.platform.aiassit.chat.history.service.impl;

import ai.platform.aiassit.chat.history.convert.AiChatMessageConvert;
import ai.platform.aiassit.chat.history.entity.AiChatMessageEntity;
import ai.platform.aiassit.chat.history.entity.dto.AiChatMessageDTO;
import ai.platform.aiassit.chat.history.entity.req.AiChatHistoryQueryRequest;
import ai.platform.aiassit.chat.history.mapper.AiChatMessageMapper;
import ai.platform.aiassit.chat.history.service.AiChatMessageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class AiChatMessageServiceImpl
        extends BaseMapperService<AiChatMessageEntity, AiChatMessageMapper, AiChatMessageDTO>
        implements AiChatMessageService {

    private final AiChatMessageConvert aiChatMessageConvert;

    public AiChatMessageServiceImpl(AiChatMessageConvert aiChatMessageConvert) {
        this.aiChatMessageConvert = aiChatMessageConvert;
    }

    @Override
    protected IConvert<AiChatMessageEntity, AiChatMessageDTO> convert() {
        return aiChatMessageConvert;
    }

    public AiChatMessageDTO newDTO() {
        return new AiChatMessageDTO();
    }

    public AiChatMessageEntity newEntity() {
        return new AiChatMessageEntity();
    }

    @Override
    protected <Query extends BaseRequest> QueryWrapper<AiChatMessageEntity> buildQuery(Query query) {
        QueryWrapper<AiChatMessageEntity> wrapper = super.buildQuery(query);
        if (query instanceof AiChatHistoryQueryRequest req) {
            if (StringUtils.hasText(req.getSessionCode())) {
                wrapper.lambda().eq(AiChatMessageEntity::getSessionCode, req.getSessionCode());
            }
            if (StringUtils.hasText(req.getRoundCode())) {
                wrapper.lambda().eq(AiChatMessageEntity::getRoundCode, req.getRoundCode());
            }
            if (StringUtils.hasText(req.getRole())) {
                wrapper.lambda().eq(AiChatMessageEntity::getRole, req.getRole());
            }
            wrapper.lambda().orderByAsc(AiChatMessageEntity::getRoundCode, AiChatMessageEntity::getSortNo, AiChatMessageEntity::getId);
        }
        return wrapper;
    }
}

package ai.platform.aiassist.service.ai.kb.service.impl;

import ai.platform.aiassist.service.ai.kb.convert.AiKbDocumentVersionConvert;
import ai.platform.aiassist.service.ai.kb.entity.AiKbDocumentVersionEntity;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbDocumentVersionDTO;
import ai.platform.aiassist.service.ai.kb.mapper.AiKbDocumentVersionMapper;
import ai.platform.aiassist.service.ai.kb.service.AiKbDocumentVersionService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class AiKbDocumentVersionServiceImpl
        extends BaseMapperService<AiKbDocumentVersionEntity, AiKbDocumentVersionMapper, AiKbDocumentVersionDTO>
        implements AiKbDocumentVersionService {

    private final AiKbDocumentVersionConvert convert;

    public AiKbDocumentVersionServiceImpl(AiKbDocumentVersionConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<AiKbDocumentVersionEntity, AiKbDocumentVersionDTO> convert() {
        return convert;
    }

    public AiKbDocumentVersionDTO newDTO() {
        return new AiKbDocumentVersionDTO();
    }

    public AiKbDocumentVersionEntity newEntity() {
        return new AiKbDocumentVersionEntity();
    }
}

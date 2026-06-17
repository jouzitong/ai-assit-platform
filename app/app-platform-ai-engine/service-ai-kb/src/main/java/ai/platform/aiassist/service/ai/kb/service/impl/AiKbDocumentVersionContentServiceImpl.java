package ai.platform.aiassist.service.ai.kb.service.impl;

import ai.platform.aiassist.service.ai.kb.convert.AiKbDocumentVersionContentConvert;
import ai.platform.aiassist.service.ai.kb.entity.AiKbDocumentVersionContentEntity;
import ai.platform.aiassist.service.ai.kb.entity.dto.AiKbDocumentVersionContentDTO;
import ai.platform.aiassist.service.ai.kb.mapper.AiKbDocumentVersionContentMapper;
import ai.platform.aiassist.service.ai.kb.service.AiKbDocumentVersionContentService;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.athena.framework.data.mybatis.service.BaseMapperService;
import org.springframework.stereotype.Service;

@Service
public class AiKbDocumentVersionContentServiceImpl
        extends BaseMapperService<AiKbDocumentVersionContentEntity, AiKbDocumentVersionContentMapper, AiKbDocumentVersionContentDTO>
        implements AiKbDocumentVersionContentService {

    private final AiKbDocumentVersionContentConvert convert;

    public AiKbDocumentVersionContentServiceImpl(AiKbDocumentVersionContentConvert convert) {
        this.convert = convert;
    }

    @Override
    protected IConvert<AiKbDocumentVersionContentEntity, AiKbDocumentVersionContentDTO> convert() {
        return convert;
    }

    public AiKbDocumentVersionContentDTO newDTO() {
        return new AiKbDocumentVersionContentDTO();
    }

    public AiKbDocumentVersionContentEntity newEntity() {
        return new AiKbDocumentVersionContentEntity();
    }
}

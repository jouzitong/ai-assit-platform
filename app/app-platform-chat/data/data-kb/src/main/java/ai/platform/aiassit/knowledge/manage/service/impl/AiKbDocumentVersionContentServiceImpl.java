package ai.platform.aiassit.knowledge.manage.service.impl;

import ai.platform.aiassit.knowledge.manage.convert.AiKbDocumentVersionContentConvert;
import ai.platform.aiassit.knowledge.manage.entity.document.AiKbDocumentVersionContentEntity;
import ai.platform.aiassit.knowledge.manage.entity.document.dto.AiKbDocumentVersionContentDTO;
import ai.platform.aiassit.knowledge.manage.mapper.AiKbDocumentVersionContentMapper;
import ai.platform.aiassit.knowledge.manage.service.AiKbDocumentVersionContentService;
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

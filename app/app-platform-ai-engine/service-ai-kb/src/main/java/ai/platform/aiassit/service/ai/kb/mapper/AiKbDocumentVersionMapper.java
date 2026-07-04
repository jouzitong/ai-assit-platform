package ai.platform.aiassit.service.ai.kb.mapper;

import ai.platform.aiassit.service.ai.kb.entity.AiKbDocumentVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiKbDocumentVersionMapper extends CrudMapper<AiKbDocumentVersionEntity> {
}

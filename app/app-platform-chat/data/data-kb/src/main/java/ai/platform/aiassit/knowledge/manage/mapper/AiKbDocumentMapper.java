package ai.platform.aiassit.knowledge.manage.mapper;

import ai.platform.aiassit.knowledge.manage.entity.document.AiKbDocumentEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

@Mapper
public interface AiKbDocumentMapper extends CrudMapper<AiKbDocumentEntity> {
}

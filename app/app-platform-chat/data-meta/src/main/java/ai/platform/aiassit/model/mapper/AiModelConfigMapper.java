package ai.platform.aiassit.model.mapper;

import ai.platform.aiassit.service.ai.api.dto.AiEnabledModelDTO;
import ai.platform.aiassit.model.entity.AiModelConfigEntity;
import org.apache.ibatis.annotations.Mapper;
import org.athena.framework.data.mybatis.mapper.CrudMapper;

import java.util.List;

@Mapper
public interface AiModelConfigMapper extends CrudMapper<AiModelConfigEntity> {

    List<AiEnabledModelDTO> selectEnabledModels();
}

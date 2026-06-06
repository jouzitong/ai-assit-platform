package ai.platform.aiassist.service.ai.meta.mapper;

import ai.platform.aiassist.service.ai.meta.entity.dto.AiModelManageDTO;
import ai.platform.aiassist.service.ai.meta.entity.req.AiModelManageQueryRequest;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiModelManageMapper {

    List<AiModelManageDTO> pageAggregate(Page<AiModelManageDTO> page,
                                         @Param("query") AiModelManageQueryRequest query);

    AiModelManageDTO selectByModelId(@Param("id") Long id);
}

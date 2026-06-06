package ai.platform.aiassit.chat.meta.mapper;

import ai.platform.aiassit.chat.meta.entity.dto.AiModelManageDTO;
import ai.platform.aiassit.chat.meta.entity.req.AiModelManageQueryRequest;
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

package ai.platform.aiassit.service.ai.meta.mapper;

import ai.platform.aiassit.service.ai.meta.entity.req.AiModelManageQueryRequest;
import ai.platform.aiassit.service.ai.meta.entity.vo.AiModelManageVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AiModelManageMapper {

    List<AiModelManageVO> pageAggregate(Page<AiModelManageVO> page,
                                        @Param("query") AiModelManageQueryRequest query);

    AiModelManageVO selectByModelId(@Param("id") Long id);
}

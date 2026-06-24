package ai.platform.aiassit.render.data.render.service;

import ai.platform.aiassit.render.data.render.entity.dto.RenderPageCategoryDTO;
import ai.platform.aiassit.render.data.render.entity.req.RenderPageCategoryQueryRequest;
import ai.platform.aiassit.render.data.render.entity.vo.RenderPageCategoryTreeVO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

import java.util.List;

public interface RenderPageCategoryService extends IMapperService<RenderPageCategoryDTO> {

    RenderPageCategoryDTO queryByCode(String code);

    List<RenderPageCategoryTreeVO> queryTree(RenderPageCategoryQueryRequest request);
}

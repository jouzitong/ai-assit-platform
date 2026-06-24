package ai.platform.aiassit.render.data.component.service;

import ai.platform.aiassit.render.data.component.entity.req.RenderComponentManageQueryRequest;
import ai.platform.aiassit.render.data.component.entity.req.RenderComponentManageRequest;
import ai.platform.aiassit.render.data.component.entity.req.RenderComponentStatusUpdateRequest;
import ai.platform.aiassit.render.data.component.entity.vo.RenderComponentCategoryVO;
import ai.platform.aiassit.render.data.component.entity.vo.RenderComponentManageVO;
import ai.platform.aiassit.render.data.component.entity.vo.RenderComponentManageSummaryVO;
import org.athena.framework.data.jdbc.vo.PageResultVO;

import java.util.List;

public interface RenderComponentManageService {

    PageResultVO<RenderComponentManageVO> page(RenderComponentManageQueryRequest request);

    RenderComponentManageSummaryVO summary();

    List<RenderComponentCategoryVO> categories();

    RenderComponentManageVO get(Long id);

    RenderComponentManageVO add(RenderComponentManageRequest request);

    RenderComponentManageVO update(Long id, RenderComponentManageRequest request);

    RenderComponentManageVO updateStatus(Long id, RenderComponentStatusUpdateRequest request);

    boolean delete(Long id);
}

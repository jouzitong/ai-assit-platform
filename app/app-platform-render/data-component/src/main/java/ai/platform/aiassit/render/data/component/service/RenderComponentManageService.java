package ai.platform.aiassit.render.data.component.service;

import ai.platform.aiassit.render.data.component.entity.req.RenderComponentManageQueryRequest;
import ai.platform.aiassit.render.data.component.entity.req.RenderComponentManageRequest;
import ai.platform.aiassit.render.data.component.entity.vo.RenderComponentManageVO;
import org.athena.framework.data.jdbc.vo.PageResultVO;

public interface RenderComponentManageService {

    PageResultVO<RenderComponentManageVO> page(RenderComponentManageQueryRequest request);

    RenderComponentManageVO get(Long id);

    RenderComponentManageVO add(RenderComponentManageRequest request);

    RenderComponentManageVO update(Long id, RenderComponentManageRequest request);

    boolean delete(Long id);
}

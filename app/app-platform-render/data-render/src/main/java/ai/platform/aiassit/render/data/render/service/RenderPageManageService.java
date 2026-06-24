package ai.platform.aiassit.render.data.render.service;

import ai.platform.aiassit.render.data.render.entity.req.RenderPageManageQueryRequest;
import ai.platform.aiassit.render.data.render.entity.req.RenderPageManageRequest;
import ai.platform.aiassit.render.data.render.entity.req.RenderPageTreeQueryRequest;
import ai.platform.aiassit.render.data.render.entity.vo.RenderPageManageVO;
import ai.platform.aiassit.render.data.render.entity.vo.RenderPageTreeVO;
import org.athena.framework.data.jdbc.vo.PageResultVO;

public interface RenderPageManageService {

    PageResultVO<RenderPageManageVO> page(RenderPageManageQueryRequest request);

    RenderPageTreeVO tree(RenderPageTreeQueryRequest request);

    RenderPageManageVO get(Long id);

    RenderPageManageVO add(RenderPageManageRequest request);

    RenderPageManageVO update(Long id, RenderPageManageRequest request);

    boolean delete(Long id);
}

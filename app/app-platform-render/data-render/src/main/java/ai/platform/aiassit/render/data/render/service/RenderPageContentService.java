package ai.platform.aiassit.render.data.render.service;

import ai.platform.aiassit.render.data.render.entity.dto.RenderPageContentDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

public interface RenderPageContentService extends IMapperService<RenderPageContentDTO> {

    RenderPageContentDTO queryByPageCode(String pageCode);
}

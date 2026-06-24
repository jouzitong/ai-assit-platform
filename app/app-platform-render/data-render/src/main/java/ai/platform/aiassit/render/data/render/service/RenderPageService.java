package ai.platform.aiassit.render.data.render.service;

import ai.platform.aiassit.render.data.render.entity.dto.RenderPageDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

public interface RenderPageService extends IMapperService<RenderPageDTO> {

    RenderPageDTO queryByCode(String code);
}

package ai.platform.aiassit.render.data.component.service;

import ai.platform.aiassit.render.data.component.entity.dto.RenderComponentDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

public interface RenderComponentService extends IMapperService<RenderComponentDTO> {

    RenderComponentDTO queryByKey(String key);
}

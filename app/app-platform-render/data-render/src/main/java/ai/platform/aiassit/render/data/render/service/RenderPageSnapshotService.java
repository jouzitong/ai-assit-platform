package ai.platform.aiassit.render.data.render.service;

import ai.platform.aiassit.render.data.render.entity.dto.RenderPageSnapshotDTO;
import org.athena.framework.data.jdbc.serivce.IMapperService;

public interface RenderPageSnapshotService extends IMapperService<RenderPageSnapshotDTO> {

    Integer nextSnapshotVersion(String pageCode);
}

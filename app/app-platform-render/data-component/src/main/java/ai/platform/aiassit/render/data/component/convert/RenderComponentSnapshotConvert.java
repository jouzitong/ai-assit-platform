package ai.platform.aiassit.render.data.component.convert;

import ai.platform.aiassit.render.data.component.entity.RenderComponentSnapshotEntity;
import ai.platform.aiassit.render.data.component.entity.dto.RenderComponentSnapshotDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        builder = @org.mapstruct.Builder(disableBuilder = true),
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface RenderComponentSnapshotConvert extends IConvert<RenderComponentSnapshotEntity, RenderComponentSnapshotDTO> {
}

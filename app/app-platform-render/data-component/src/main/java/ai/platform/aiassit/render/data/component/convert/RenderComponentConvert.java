package ai.platform.aiassit.render.data.component.convert;

import ai.platform.aiassit.render.data.component.entity.RenderComponentEntity;
import ai.platform.aiassit.render.data.component.entity.dto.RenderComponentDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        builder = @org.mapstruct.Builder(disableBuilder = true),
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface RenderComponentConvert extends IConvert<RenderComponentEntity, RenderComponentDTO> {
}

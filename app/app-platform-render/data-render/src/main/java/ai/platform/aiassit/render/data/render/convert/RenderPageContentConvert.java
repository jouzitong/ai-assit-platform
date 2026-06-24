package ai.platform.aiassit.render.data.render.convert;

import ai.platform.aiassit.render.data.render.entity.RenderPageContentEntity;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageContentDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        builder = @org.mapstruct.Builder(disableBuilder = true),
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface RenderPageContentConvert extends IConvert<RenderPageContentEntity, RenderPageContentDTO> {
}

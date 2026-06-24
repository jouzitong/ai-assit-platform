package ai.platform.aiassit.render.data.render.convert;

import ai.platform.aiassit.render.data.render.entity.RenderPageCategoryEntity;
import ai.platform.aiassit.render.data.render.entity.dto.RenderPageCategoryDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        builder = @org.mapstruct.Builder(disableBuilder = true),
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface RenderPageCategoryConvert extends IConvert<RenderPageCategoryEntity, RenderPageCategoryDTO> {
}

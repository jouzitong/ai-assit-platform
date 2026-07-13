package ai.platform.aiassit.data.virtualization.data.convert;

import ai.platform.aiassit.data.virtualization.data.dto.VirtualRelationDTO;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualRelationEntity;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", builder = @org.mapstruct.Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VirtualRelationConvert extends IConvert<VirtualRelationEntity, VirtualRelationDTO> {
}

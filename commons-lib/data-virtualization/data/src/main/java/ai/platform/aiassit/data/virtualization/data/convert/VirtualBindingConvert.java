package ai.platform.aiassit.data.virtualization.data.convert;

import ai.platform.aiassit.data.virtualization.data.dto.VirtualBindingDTO;
import ai.platform.aiassit.data.virtualization.data.entity.VirtualBindingEntity;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", builder = @org.mapstruct.Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface VirtualBindingConvert extends IConvert<VirtualBindingEntity, VirtualBindingDTO> {
}

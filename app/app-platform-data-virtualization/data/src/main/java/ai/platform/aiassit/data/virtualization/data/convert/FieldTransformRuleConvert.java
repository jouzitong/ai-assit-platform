package ai.platform.aiassit.data.virtualization.data.convert;

import ai.platform.aiassit.data.virtualization.data.dto.FieldTransformRuleDTO;
import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformRuleEntity;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", builder = @org.mapstruct.Builder(disableBuilder = true), unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface FieldTransformRuleConvert extends IConvert<FieldTransformRuleEntity, FieldTransformRuleDTO> {
}

package ai.platform.aiassit.user.errcode.data.convert;

import ai.platform.aiassit.user.errcode.data.entity.ErrCodeEntity;
import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ErrCodeConvert extends IConvert<ErrCodeEntity, ErrCodeDTO> {
}

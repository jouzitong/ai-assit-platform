package ai.platform.aiassit.user.errcode.data.convert;

import ai.platform.aiassit.user.errcode.data.entity.ErrCodeI18nEntity;
import ai.platform.aiassit.user.errcode.data.entity.dto.ErrCodeI18nDTO;
import org.athena.framework.data.jdbc.convert.IConvert;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ErrCodeI18nConvert extends IConvert<ErrCodeI18nEntity, ErrCodeI18nDTO> {
}

package ai.platform.aiassit.user.system.settings.data.entity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.mybatis.entity.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = true)
public class SystemSettingDTO extends BaseDTO {

    private String settingKey;

    private String description;

    private String settingValue;

    private String valueType;

    private Boolean enabled;
}

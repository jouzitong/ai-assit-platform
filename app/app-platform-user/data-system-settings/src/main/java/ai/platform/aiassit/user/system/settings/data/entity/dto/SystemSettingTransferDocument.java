package ai.platform.aiassit.user.system.settings.data.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SystemSettingTransferDocument {

    private String settingKey;

    private String description;

    private String settingValue;

    private String valueType;

    private Boolean enabled;
}

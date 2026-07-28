package ai.platform.aiassit.user.system.settings.data.entity.req;

import lombok.Data;

import java.util.List;

@Data
public class SystemSettingExportRequest {

    private List<String> settingKeys;

    private String keyword;
}

package ai.platform.aiassit.user.system.settings.data.entity.dto;

import lombok.Data;

@Data
public class SystemSettingImportResultDTO {

    private int received;

    private int created;

    private int updated;

    private int skipped;
}

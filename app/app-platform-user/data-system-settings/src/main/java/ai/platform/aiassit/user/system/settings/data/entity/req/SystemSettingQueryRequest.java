package ai.platform.aiassit.user.system.settings.data.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;

@Data
@EqualsAndHashCode(callSuper = true)
public class SystemSettingQueryRequest extends BaseRequest {

    private String keyword;

    private String settingKey;

    private String valueType;

    private Boolean enabled;
}

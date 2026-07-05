package ai.platform.aiassit.user.system.settings.data.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.annotations.IgnoredQuery;

@Data
@EqualsAndHashCode(callSuper = true)
public class SystemSettingQueryRequest extends BaseRequest {

    @IgnoredQuery
    private String keyword;

    private String settingKey;

    private String valueType;

    private Boolean enabled;
}

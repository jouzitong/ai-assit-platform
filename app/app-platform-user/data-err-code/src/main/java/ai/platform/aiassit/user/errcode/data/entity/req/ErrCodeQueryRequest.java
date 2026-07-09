package ai.platform.aiassit.user.errcode.data.entity.req;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.req.BaseRequest;
import org.athena.framework.data.mybatis.annotations.IgnoredQuery;

@Data
@EqualsAndHashCode(callSuper = true)
public class ErrCodeQueryRequest extends BaseRequest {

    @IgnoredQuery
    private String keyword;

    private Integer code;

    private Integer httpStatus;

    private String tags;
}

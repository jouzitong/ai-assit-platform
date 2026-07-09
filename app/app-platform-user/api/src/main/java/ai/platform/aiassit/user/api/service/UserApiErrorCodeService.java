package ai.platform.aiassit.user.api.service;

import ai.platform.aiassit.user.api.ErrCodeQueryApi;
import ai.platform.aiassit.user.api.dto.ErrCodeQueryRequest;
import ai.platform.aiassit.user.api.dto.ErrCodeQueryResponse;
import org.arthena.framework.common.service.ErrorCodeService;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;

public class UserApiErrorCodeService implements ErrorCodeService {

    private final ErrCodeQueryApi errCodeQueryApi;

    public UserApiErrorCodeService(ErrCodeQueryApi errCodeQueryApi) {
        this.errCodeQueryApi = errCodeQueryApi;
    }

    @Override
    public int order() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public String getMsg(Integer code, String locale) {
        if (code == null) {
            return null;
        }

        ErrCodeQueryRequest request = new ErrCodeQueryRequest();
        request.setCode(code);
        request.setLocale(locale);

        ErrCodeQueryResponse response = errCodeQueryApi.queryErrCode(request);
        if (response == null || !StringUtils.hasText(response.getMessageTemplate())) {
            return null;
        }
        return response.getMessageTemplate();
    }
}

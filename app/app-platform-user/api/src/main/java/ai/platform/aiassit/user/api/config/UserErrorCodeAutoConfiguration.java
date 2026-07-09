package ai.platform.aiassit.user.api.config;

import ai.platform.aiassit.user.api.ErrCodeQueryApi;
import ai.platform.aiassit.user.api.service.UserApiErrorCodeService;
import org.arthena.framework.common.service.ErrorCodeService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnBean(ErrCodeQueryApi.class)
public class UserErrorCodeAutoConfiguration {

    @Bean
    public ErrorCodeService userApiErrorCodeService(ErrCodeQueryApi errCodeQueryApi) {
        return new UserApiErrorCodeService(errCodeQueryApi);
    }
}

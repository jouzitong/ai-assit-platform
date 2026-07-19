package ai.platform.aiassit.db.engine.virtualization.adapter.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 默认访问策略装配；业务侧可提供自己的 {@link DataPreviewAccessPolicy} Bean 完整替换。 */
@Configuration(proxyBeanMethods = false)
public class DataPreviewAccessPolicyConfiguration {

    @Bean
    @ConditionalOnMissingBean(DataPreviewAccessPolicy.class)
    public DataPreviewAccessPolicy dataPreviewAccessPolicy() {
        return new AuthenticatedDataPreviewAccessPolicy();
    }
}

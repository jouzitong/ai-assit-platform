package ai.platform.aiassit.service.ai.provider.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** RAGFlow 提供方配置装配。 */
@Configuration
@EnableConfigurationProperties(RagflowProperties.class)
public class RagflowProviderConfiguration {
}

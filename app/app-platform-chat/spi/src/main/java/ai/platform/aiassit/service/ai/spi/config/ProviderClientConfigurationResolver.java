package ai.platform.aiassit.service.ai.spi.config;

import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;

/**
 * Resolves server-owned Provider connection data into a transient request context.
 * Implementations must never return credentials to a controller, worker process, or log.
 */
public interface ProviderClientConfigurationResolver {

    RequestMeta apply(String clientKey, AiKnowledgeClientType expectedClientType, RequestMeta requestMeta);
}

package ai.platform.aiassist.service.ai.core;

import ai.platform.aiassist.service.ai.api.chat.AiChatApi;
import ai.platform.aiassist.service.ai.api.knowledge.AiKnowledgeBaseApi;
import ai.platform.aiassist.service.ai.api.vector.AiVectorApi;

/**
 * AI 执行领域服务。
 */
public interface AiExecutionDomainService extends AiChatApi, AiKnowledgeBaseApi, AiVectorApi {
}


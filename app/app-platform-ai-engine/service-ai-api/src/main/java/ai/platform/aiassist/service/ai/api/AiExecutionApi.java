package ai.platform.aiassist.service.ai.api;

import ai.platform.aiassist.service.ai.api.chat.AiChatApi;
import ai.platform.aiassist.service.ai.api.knowledge.AiKnowledgeBaseApi;
import ai.platform.aiassist.service.ai.api.vector.AiVectorApi;

/**
 * AI 执行引擎统一聚合 API（兼容入口）。
 *
 * <p>该接口只做能力聚合，便于历史代码兼容；
 * 新业务建议按领域优先依赖以下接口：</p>
 *
 * <p>1) 对话能力：{@link AiChatApi}<br>
 * 2) 知识库能力：{@link AiKnowledgeBaseApi}<br>
 * 3) 向量与重排能力：{@link AiVectorApi}</p>
 */
public interface AiExecutionApi extends AiChatApi, AiKnowledgeBaseApi, AiVectorApi {
}

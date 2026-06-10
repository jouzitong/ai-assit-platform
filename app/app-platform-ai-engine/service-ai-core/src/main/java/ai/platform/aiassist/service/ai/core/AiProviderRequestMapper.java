package ai.platform.aiassist.service.ai.core;

import ai.platform.aiassist.service.ai.api.dto.ChatRequest;
import ai.platform.aiassist.service.ai.api.dto.EmbedRequest;
import ai.platform.aiassist.service.ai.api.dto.KbDeleteRequest;
import ai.platform.aiassist.service.ai.api.dto.KbSearchRequest;
import ai.platform.aiassist.service.ai.api.dto.KbUpsertRequest;
import ai.platform.aiassist.service.ai.api.dto.RerankRequest;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderChatRequest;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderEmbedRequest;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderKbDeleteRequest;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderKbSearchRequest;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderKbUpsertRequest;
import ai.platform.aiassist.service.ai.spi.provider.dto.ProviderRerankRequest;
import org.springframework.stereotype.Component;

/**
 * AI Provider 请求参数映射器。
 *
 * <p>负责将平台 API 层的统一请求对象转换为 SPI Provider 层的请求对象，
 * 屏蔽业务层与不同模型服务商请求结构之间的差异。</p>
 */
@Component
public class AiProviderRequestMapper {

    /**
     * 映射聊天请求参数。
     *
     * <p>将平台统一的聊天请求转换为 Provider 聊天请求，并在请求未指定模型时，
     * 使用核心配置中的默认聊天模型。</p>
     *
     * @param request 聊天请求参数
     * @param properties AI 核心配置属性
     * @return Provider 聊天请求参数
     */
    public ProviderChatRequest mapChat(ChatRequest request, AiCoreProperties properties) {
        ProviderChatRequest target = new ProviderChatRequest();
        target.setModel(resolveModel(request.getModel(), properties.getDefaultChatModel()));
        target.setMessages(request.getMessages());
        target.setTools(request.getTools());
        target.setResponseFormat(request.getResponseFormat());
        if (request.getOptions() != null) {
            target.setTemperature(request.getOptions().getTemperature());
            target.setTopP(request.getOptions().getTopP());
            target.setMaxTokens(request.getOptions().getMaxTokens());
            target.setTimeoutMs(request.getOptions().getTimeoutMs());
        }
        target.setMeta(request.getMeta());
        target.setExt(request.getExt());
        return target;
    }

    /**
     * 映射向量化请求参数。
     *
     * <p>将平台统一的文本向量化请求转换为 Provider 向量化请求，并在请求未指定模型时，
     * 使用核心配置中的默认向量模型。</p>
     *
     * @param request 向量化请求参数
     * @param properties AI 核心配置属性
     * @return Provider 向量化请求参数
     */
    public ProviderEmbedRequest mapEmbed(EmbedRequest request, AiCoreProperties properties) {
        ProviderEmbedRequest target = new ProviderEmbedRequest();
        target.setModel(resolveModel(request.getModel(), properties.getDefaultEmbeddingModel()));
        target.setInputs(request.getInputs());
        target.setMeta(request.getMeta());
        return target;
    }

    /**
     * 映射重排序请求参数。
     *
     * <p>将平台统一的重排序请求转换为 Provider 重排序请求，并在请求未指定模型时，
     * 使用核心配置中的默认重排序模型。</p>
     *
     * @param request 重排序请求参数
     * @param properties AI 核心配置属性
     * @return Provider 重排序请求参数
     */
    public ProviderRerankRequest mapRerank(RerankRequest request, AiCoreProperties properties) {
        ProviderRerankRequest target = new ProviderRerankRequest();
        target.setModel(resolveModel(request.getModel(), properties.getDefaultRerankModel()));
        target.setQuery(request.getQuery());
        target.setCandidates(request.getCandidates());
        target.setTopN(request.getTopN());
        target.setMeta(request.getMeta());
        return target;
    }

    /**
     * 映射知识库文档写入请求参数。
     *
     * <p>将平台统一的知识库文档新增或更新请求转换为 Provider 知识库写入请求。</p>
     *
     * @param request 知识库文档写入请求参数
     * @return Provider 知识库文档写入请求参数
     */
    public ProviderKbUpsertRequest mapKbUpsert(KbUpsertRequest request) {
        ProviderKbUpsertRequest target = new ProviderKbUpsertRequest();
        target.setKbId(request.getKbId());
        target.setDocuments(request.getDocuments());
        target.setMeta(request.getMeta());
        return target;
    }

    /**
     * 映射知识库文档删除请求参数。
     *
     * <p>将平台统一的知识库文档删除请求转换为 Provider 知识库删除请求。</p>
     *
     * @param request 知识库文档删除请求参数
     * @return Provider 知识库文档删除请求参数
     */
    public ProviderKbDeleteRequest mapKbDelete(KbDeleteRequest request) {
        ProviderKbDeleteRequest target = new ProviderKbDeleteRequest();
        target.setKbId(request.getKbId());
        target.setDocumentIds(request.getDocumentIds());
        target.setMeta(request.getMeta());
        return target;
    }

    /**
     * 映射知识库检索请求参数。
     *
     * <p>将平台统一的知识库检索请求转换为 Provider 知识库检索请求。</p>
     *
     * @param request 知识库检索请求参数
     * @return Provider 知识库检索请求参数
     */
    public ProviderKbSearchRequest mapKbSearch(KbSearchRequest request) {
        ProviderKbSearchRequest target = new ProviderKbSearchRequest();
        target.setKbId(request.getKbId());
        target.setQuery(request.getQuery());
        target.setTopK(request.getTopK());
        target.setMeta(request.getMeta());
        return target;
    }

    /**
     * 解析最终使用的模型名称。
     *
     * <p>当请求中未指定模型名称时，返回配置中的默认模型；否则返回请求指定的模型。</p>
     *
     * @param requestedModel 请求中指定的模型名称
     * @param defaultModel 默认模型名称
     * @return 最终使用的模型名称
     */
    private String resolveModel(String requestedModel, String defaultModel) {
        if (requestedModel == null || requestedModel.isBlank()) {
            return defaultModel;
        }
        return requestedModel;
    }
}

package ai.platform.aiassit.service.ai.spi.provider.dto;

import lombok.Data;

/**
 * AI 服务商模型列表查询参数。
 *
 * <p>用于调用 OpenAI 兼容服务的 {@code GET /v1/models} 接口。请求参数可覆盖
 * 提供方的默认连接配置，便于在模型配置保存前校验可用模型。</p>
 */
@Data
public class ProviderModelListRequest {

    /** 服务地址，例如 {@code https://api.openai.com/v1}。 */
    private String baseUrl;

    /** API Key，不得写入日志。 */
    private String apiKey;

    /** 请求超时时间，单位为毫秒。 */
    private Integer timeoutMs;
}

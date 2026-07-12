package ai.platform.aiassit.service.ai.spi.provider.dto;

import lombok.Data;

/**
 * AI 服务商模型信息。
 *
 * <p>字段与 OpenAI {@code /v1/models} 接口的标准模型对象保持一致。</p>
 */
@Data
public class ProviderModel {

    /** 模型唯一标识，例如 {@code gpt-4o}。 */
    private String id;

    /** 对象类型，标准值通常为 {@code model}。 */
    private String object;

    /** 创建时间的 Unix 秒级时间戳。 */
    private Long created;

    /** 模型所属方，对应标准字段 {@code owned_by}。 */
    private String ownedBy;
}

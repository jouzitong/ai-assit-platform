package ai.platform.aiassist.service.ai.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class AiProviderModelOverviewDTO {

    /**
     * 提供商列表。
     */
    private List<ProviderItem> providers;

    /**
     * 提供商总数。
     */
    private Integer providerCount;

    /**
     * 模型总数。
     */
    private Integer modelCount;

    @Data
    public static class ProviderItem {

        private Long id;

        private String providerCode;

        private String providerName;

        private String baseUrl;

        private Integer connectTimeoutMs;

        private Integer readTimeoutMs;

        private Boolean enabled;

        private String remark;

        /**
         * 当前提供商下的模型数量。
         */
        private Integer modelCount;

        /**
         * 当前提供商下的模型基础配置列表。
         */
        private List<ModelItem> models;
    }

    @Data
    public static class ModelItem {

        private Long id;

        private String modelCode;

        private String modelName;

        private String providerCode;

        private String apiModel;

        private String capabilityTags;

        private Integer maxContextTokens;

        private Integer maxOutputTokens;

        private Integer temperatureEnabled;

        private Boolean enabled;

        private Integer priority;

        private String remark;
    }
}

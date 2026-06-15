package ai.platform.aiassist.service.ai.provider.config;

import com.aliyun.bailian20231229.Client;
import com.aliyun.teaopenapi.models.Config;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(QwenProperties.class)
@ConditionalOnProperty(prefix = "ai.provider.qwen", name = "enabled", havingValue = "true")
public class QwenProviderConfiguration {

    @Bean
    public OpenAiApi qwenOpenAiApi(QwenProperties properties) {
        return OpenAiApi.builder()
                .baseUrl(properties.getBaseUrl())
                .apiKey(requireApiKey(properties))
                .build();
    }

    @Bean
    public OpenAiChatModel qwenChatModel(OpenAiApi qwenOpenAiApi, QwenProperties properties) {
        return OpenAiChatModel.builder()
                .openAiApi(qwenOpenAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(properties.getDefaultModel()).build())
                .retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
                .observationRegistry(ObservationRegistry.NOOP)
                .build();
    }

    @Bean
    public OpenAiEmbeddingModel qwenEmbeddingModel(OpenAiApi qwenOpenAiApi) {
        return new OpenAiEmbeddingModel(
                qwenOpenAiApi,
                org.springframework.ai.document.MetadataMode.NONE,
                OpenAiEmbeddingOptions.builder().model("text-embedding-v3").build(),
                RetryUtils.DEFAULT_RETRY_TEMPLATE,
                ObservationRegistry.NOOP
        );
    }

    @Bean
    public Client qwenBailianClient(QwenProperties properties) throws Exception {
        Config config = new Config()
                .setAccessKeyId(resolveAccessKeyId(properties))
                .setAccessKeySecret(resolveAccessKeySecret(properties));
        config.endpoint = properties.getBailianEndpoint();
        return new Client(config);
    }

    private String requireApiKey(QwenProperties properties) {
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("ai.provider.qwen.api-key must not be empty");
        }
        return properties.getApiKey();
    }

    private String resolveAccessKeyId(QwenProperties properties) {
        if (StringUtils.hasText(properties.getAccessKeyId())) {
            return properties.getAccessKeyId();
        }
        String value = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_ID");
        if (StringUtils.hasText(value)) {
            return value;
        }
        throw new IllegalStateException("ALIBABA_CLOUD_ACCESS_KEY_ID must not be empty");
    }

    private String resolveAccessKeySecret(QwenProperties properties) {
        if (StringUtils.hasText(properties.getAccessKeySecret())) {
            return properties.getAccessKeySecret();
        }
        String value = System.getenv("ALIBABA_CLOUD_ACCESS_KEY_SECRET");
        if (StringUtils.hasText(value)) {
            return value;
        }
        throw new IllegalStateException("ALIBABA_CLOUD_ACCESS_KEY_SECRET must not be empty");
    }
}

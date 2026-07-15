package ai.platform.aiassit.db.engine.virtualization.adapter.external;

import ai.platform.aiassit.data.virtualization.spi.text.TextGenerationCommand;
import ai.platform.aiassit.data.virtualization.spi.text.TextGenerationPort;
import ai.platform.aiassit.data.virtualization.spi.text.TextGenerationResult;
import ai.platform.aiassit.service.ai.api.AiTextGenerationApi;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationRequest;
import ai.platform.aiassit.service.ai.api.dto.AiTextGenerationResponse;
import ai.platform.aiassit.user.system.settings.api.SystemSettingInternalApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.athena.framework.web.vo.R;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Keeps Chat text-generation HTTP DTOs outside the virtualization core. */
@Component
public class AiTextGenerationAdapter implements TextGenerationPort {

    /**
     * DB Engine 业务 AI 默认模型配置。
     *
     * <p>用途：为 DB Engine 内部文本生成选择平台模型并统一生成参数。</p>
     * <p>类型：JSON_OBJECT。</p>
     * <p>格式：{@code {"modelCode":"client.model","maxTokens":2048,"temperature":0.2}}；
     * modelCode 对应 {@code ai_model_config.model_code}，maxTokens 必须为正整数，temperature
     * 必须在 0 到 1 之间。</p>
     * <p>要求：三个字段均必填；配置缺失或格式错误时拒绝执行文本生成，不使用本地默认模型。</p>
     * <p>敏感性：不包含密钥；模型的 Base URL、API Key 和真实 apiModel 由 Chat 服务按
     * modelCode 查询模型配置后解析，禁止放入此配置。</p>
     * <p>生效：每次生成前实时读取，不缓存，无需重启。</p>
     */
    public static final String DEFAULT_AI_MODEL_SETTING_KEY = "dbEngine.biz.ai.default.model";

    private final AiTextGenerationApi textGenerationApi;
    private final SystemSettingInternalApi systemSettingInternalApi;
    private final ObjectMapper objectMapper;

    public AiTextGenerationAdapter(AiTextGenerationApi textGenerationApi,
                                   SystemSettingInternalApi systemSettingInternalApi,
                                   ObjectMapper objectMapper) {
        this.textGenerationApi = textGenerationApi;
        this.systemSettingInternalApi = systemSettingInternalApi;
        this.objectMapper = objectMapper;
    }

    @Override
    public TextGenerationResult generate(TextGenerationCommand command) {
        if (command == null || command.userPrompt() == null || command.userPrompt().isBlank()) {
            throw new IllegalArgumentException("text generation command/userPrompt 不能为空");
        }
        TextGenerationSetting setting = loadSetting();
        AiTextGenerationRequest request = new AiTextGenerationRequest();
        request.setModelCode(setting.modelCode());
        request.setSystemPrompt(command.systemPrompt());
        request.setUserPrompt(command.userPrompt());
        request.setScene(command.scene());
        request.setMaxTokens(setting.maxTokens());
        request.setTemperature(setting.temperature());
        R<AiTextGenerationResponse> response = textGenerationApi.generate(request);
        if (response == null) {
            throw new IllegalStateException("文本生成失败: 无响应");
        }
        if (!response.isOk()) {
            throw new IllegalStateException("文本生成失败, code=" + response.getCode());
        }
        if (response.getData() == null || response.getData().getText() == null
                || response.getData().getText().isBlank()) {
            throw new IllegalStateException("文本生成失败: 响应文本为空");
        }
        return new TextGenerationResult(response.getData().getText());
    }

    private TextGenerationSetting loadSetting() {
        R<String> response = systemSettingInternalApi.queryValueByKey(DEFAULT_AI_MODEL_SETTING_KEY);
        if (response == null || !response.isOk() || !StringUtils.hasText(response.getData())) {
            throw invalidSetting(null);
        }
        try {
            JsonNode root = objectMapper.readTree(response.getData());
            if (root == null || !root.isObject()) {
                throw invalidSetting(null);
            }
            String modelCode = text(root.get("modelCode"));
            JsonNode maxTokensNode = root.get("maxTokens");
            JsonNode temperatureNode = root.get("temperature");
            if (!StringUtils.hasText(modelCode)
                    || maxTokensNode == null || !maxTokensNode.isIntegralNumber()
                    || !maxTokensNode.canConvertToInt() || maxTokensNode.intValue() <= 0
                    || temperatureNode == null || !temperatureNode.isNumber()
                    || temperatureNode.doubleValue() < 0D || temperatureNode.doubleValue() > 1D) {
                throw invalidSetting(null);
            }
            return new TextGenerationSetting(
                    modelCode.trim(),
                    maxTokensNode.intValue(),
                    temperatureNode.doubleValue());
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidSetting(ex);
        }
    }

    private String text(JsonNode node) {
        return node != null && node.isTextual() ? node.textValue() : null;
    }

    private IllegalStateException invalidSetting(Throwable cause) {
        return new IllegalStateException("DB Engine AI 默认模型配置无效: " + DEFAULT_AI_MODEL_SETTING_KEY, cause);
    }

    private record TextGenerationSetting(String modelCode, int maxTokens, double temperature) {
    }
}

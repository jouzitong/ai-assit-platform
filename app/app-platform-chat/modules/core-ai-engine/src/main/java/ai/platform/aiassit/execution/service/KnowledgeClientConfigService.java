package ai.platform.aiassit.execution.service;

import ai.platform.aiassit.service.ai.api.constant.AiChatBizCodeConstant;
import ai.platform.aiassit.service.ai.api.dto.AiKbAuthConfig;
import ai.platform.aiassit.service.ai.api.dto.RequestMeta;
import ai.platform.aiassit.service.ai.api.enums.AiKbAuthType;
import ai.platform.aiassit.service.ai.api.enums.AiKnowledgeClientType;
import ai.platform.aiassit.user.system.settings.api.SystemSettingInternalApi;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.arthena.framework.common.exception.BizException;
import org.athena.framework.web.vo.R;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从系统参数解析知识库客户端配置，并在服务端将认证信息注入 Provider 调用上下文。
 *
 * <p>系统参数 {@value #SETTING_KEY} 的兼容格式为 {@code key/type/url/apikey}；建议使用
 * {@code auth} 对象扩展认证，例如 {@code {"type":"bearer","value":"..."}} 或
 * {@code {"type":"aliyun_aksk","accessKeyId":"...","accessKeySecret":"..."}}。
 * 管理端只会得到非敏感摘要。</p>
 */
@Service
public class KnowledgeClientConfigService {

    public static final String SETTING_KEY = "chat.engine.kb.client.list";
    public static final String CLIENT_KEY_EXT = "knowledgeClientKey";

    private final SystemSettingInternalApi systemSettingInternalApi;
    private final ObjectMapper objectMapper;

    public KnowledgeClientConfigService(SystemSettingInternalApi systemSettingInternalApi, ObjectMapper objectMapper) {
        this.systemSettingInternalApi = systemSettingInternalApi;
        this.objectMapper = objectMapper;
    }

    public List<KnowledgeClientOption> listOptions() {
        return loadClients().stream().map(this::toOption).toList();
    }

    public KnowledgeClientOption requireOption(String clientKey) {
        return toOption(requireClient(clientKey));
    }

    /**
     * 获取项目唯一启用的知识库客户端。
     *
     * <p>当前知识库管理只支持一个 Provider 平台；客户端地址与凭据统一由系统参数维护，
     * 不允许再由单个知识库存储或页面覆盖。</p>
     */
    public KnowledgeClientOption requireSingleOption() {
        List<ConfiguredClient> clients = loadClients();
        if (clients.size() != 1) {
            throw invalidClientConfig("exactly one knowledge client is required");
        }
        return toOption(clients.get(0));
    }

    /** 将唯一系统客户端的地址和认证信息注入 Provider 调用上下文。 */
    public RequestMeta applySingle(RequestMeta requestMeta) {
        KnowledgeClientOption option = requireSingleOption();
        return apply(option.getKey(), option.getClientType(), requestMeta);
    }

    /** 读取系统客户端的认证对象，用于首次创建 KB 时持久化到 KB 配置。 */
    public AiKbAuthConfig resolveAuth(String clientKey) {
        return toAuthConfig(requireClient(clientKey).auth);
    }

    /**
     * 使用所选系统客户端覆盖调用地址与认证信息，并校验本地 KB 保存的 Provider 类型。
     */
    public RequestMeta apply(String clientKey, AiKnowledgeClientType expectedClientType, RequestMeta requestMeta) {
        ConfiguredClient client = requireClient(clientKey);
        if (expectedClientType != null && client.clientType != expectedClientType) {
            throw BizException.of(AiChatBizCodeConstant.KNOWLEDGE_SERVICE_NOT_FOUND, clientKey);
        }

        RequestMeta target = requestMeta == null ? new RequestMeta() : requestMeta;
        Map<String, Object> ext = target.getExt() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(target.getExt());
        ext.put(CLIENT_KEY_EXT, client.key);
        ext.put("knowledgeClientUrl", client.url);
        ext.put("knowledgeClientAuth", client.auth);

        // RAGFlow 已支持通过 RequestMeta 覆盖地址和密钥；保留该映射以兼容现有 Provider。
        if (client.clientType == AiKnowledgeClientType.RAGFLOW) {
            ext.put("ragflowBaseUrl", client.url);
            String credential = text(client.auth.get("value"));
            if (StringUtils.hasText(credential)) {
                ext.put("ragflowApiKey", credential);
            }
        }
        target.setExt(ext);
        return target;
    }

    private List<ConfiguredClient> loadClients() {
        R<String> response = systemSettingInternalApi.queryValueByKey(SETTING_KEY);
        if (response == null || response.getCode() != 0 || !StringUtils.hasText(response.getData())) {
            throw BizException.of(AiChatBizCodeConstant.KNOWLEDGE_SERVICE_NOT_FOUND, SETTING_KEY);
        }
        try {
            JsonNode root = objectMapper.readTree(response.getData());
            if (root == null || !root.isArray()) {
                throw invalidClientConfig(SETTING_KEY);
            }
            List<ConfiguredClient> clients = new ArrayList<>();
            for (JsonNode item : root) {
                ConfiguredClient client = parseClient(item);
                if (clients.stream().anyMatch(existing -> existing.key.equals(client.key))) {
                    throw invalidClientConfig(client.key);
                }
                clients.add(client);
            }
            return clients;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw invalidClientConfig(SETTING_KEY);
        }
    }

    private ConfiguredClient requireClient(String clientKey) {
        if (!StringUtils.hasText(clientKey)) {
            throw invalidClientConfig("clientKey");
        }
        return loadClients().stream()
                .filter(item -> item.key.equals(clientKey.trim()))
                .findFirst()
                .orElseThrow(() -> BizException.of(AiChatBizCodeConstant.KNOWLEDGE_SERVICE_NOT_FOUND, clientKey));
    }

    private ConfiguredClient parseClient(JsonNode item) {
        String key = text(item == null ? null : item.get("key"));
        String url = text(item == null ? null : item.get("url"));
        AiKnowledgeClientType clientType = parseClientType(item == null ? null : item.get("type"));
        if (!StringUtils.hasText(key) || !StringUtils.hasText(url) || clientType == null) {
            throw invalidClientConfig(key);
        }
        Map<String, Object> auth = parseAuth(item, clientType);
        return new ConfiguredClient(key.trim(), clientType, url.trim(), auth);
    }

    private AiKnowledgeClientType parseClientType(JsonNode node) {
        if (node == null || !node.canConvertToInt()) {
            return null;
        }
        int code = node.intValue();
        return Arrays.stream(AiKnowledgeClientType.values())
                .filter(item -> item.getCode() == code)
                .findFirst()
                .orElse(null);
    }

    private Map<String, Object> parseAuth(JsonNode item, AiKnowledgeClientType clientType) {
        Map<String, Object> auth = new LinkedHashMap<>();
        JsonNode authNode = item.get("auth");
        if (authNode != null && authNode.isObject()) {
            String type = defaultText(text(authNode.get("type")), "bearer").toLowerCase();
            auth.put("type", type);
            if ("aliyun_aksk".equals(type) || "aksk".equals(type)) {
                copyText(authNode, "accessKeyId", auth);
                copyText(authNode, "accessKeySecret", auth);
            } else {
                String value = firstText(authNode.get("value"), authNode.get("token"), authNode.get("apiKey"), authNode.get("apikey"));
                if (StringUtils.hasText(value)) {
                    auth.put("value", value);
                }
            }
        } else {
            String apiKey = firstText(item.get("apikey"), item.get("apiKey"));
            auth.put("type", clientType == AiKnowledgeClientType.RAGFLOW ? "bearer" : "none");
            if (StringUtils.hasText(apiKey)) {
                auth.put("value", apiKey);
            }
        }
        return auth;
    }

    private KnowledgeClientOption toOption(ConfiguredClient client) {
        KnowledgeClientOption option = new KnowledgeClientOption();
        option.setKey(client.key);
        option.setClientType(client.clientType);
        option.setUrl(client.url);
        option.setAuthType(defaultText(text(client.auth.get("type")), "none"));
        option.setAuthValueMasked(maskAuthValue(text(client.auth.get("value"))));
        option.setAccessKeyIdMasked(maskAuthValue(text(client.auth.get("accessKeyId"))));
        return option;
    }

    private AiKbAuthConfig toAuthConfig(Map<String, Object> auth) {
        String type = defaultText(text(auth.get("type")), "bearer").toLowerCase();
        AiKbAuthConfig config = new AiKbAuthConfig();
        if ("aliyun_aksk".equals(type) || "aksk".equals(type)) {
            config.setType(AiKbAuthType.ALIYUN_AKSK);
            config.setAccessKeyId(text(auth.get("accessKeyId")));
            config.setAccessKeySecret(text(auth.get("accessKeySecret")));
            return config;
        }
        config.setType(AiKbAuthType.BEARER);
        config.setApiKey(text(auth.get("value")));
        return config;
    }

    private String maskAuthValue(String value) {
        if (!StringUtils.hasText(value) || value.length() <= 12) {
            return StringUtils.hasText(value) ? "****" : null;
        }
        return value.substring(0, 8) + "****" + value.substring(value.length() - 4);
    }

    private void copyText(JsonNode source, String field, Map<String, Object> target) {
        String value = text(source.get(field));
        if (StringUtils.hasText(value)) {
            target.put(field, value);
        }
    }

    private String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            String value = text(node);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String text(Object value) {
        if (value instanceof JsonNode node) {
            return node.isTextual() || node.isNumber() ? node.asText().trim() : null;
        }
        return value == null ? null : String.valueOf(value).trim();
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private BizException invalidClientConfig(String detail) {
        return BizException.of(AiChatBizCodeConstant.KNOWLEDGE_SERVICE_NOT_FOUND,
                "invalid knowledge client config: " + detail);
    }

    private record ConfiguredClient(String key, AiKnowledgeClientType clientType, String url,
                                    Map<String, Object> auth) {
    }
}

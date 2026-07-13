package ai.platform.aiassit.model.entity;

import ai.platform.aiassit.service.ai.api.enums.AiChatClientType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;

import java.util.Map;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_client_config", autoResultMap = true)
public class AiClientConfigEntity extends AuditableEntity {

    @JdbcColumn(name = "client_code", dataType = "VARCHAR(64)", length = 64, nullable = false, unique = true, comment = "客户端编码")
    @TableField("client_code")
    private String clientCode;

    @JdbcColumn(name = "client_name", dataType = "VARCHAR(128)", length = 128, nullable = false, comment = "客户端名称")
    @TableField("client_name")
    private String clientName;

    @JdbcColumn(name = "client_type", dataType = "INT", nullable = false, comment = "对话客户端类型")
    @TableField(value = "client_type", typeHandler = DefaultEnumTypeHandler.class)
    private AiChatClientType clientType;

    @JdbcColumn(name = "base_url", dataType = "VARCHAR(512)", length = 512, nullable = true, comment = "提供商请求基础地址")
    @TableField("base_url")
    private String baseUrl;

    @JdbcColumn(name = "api_key", dataType = "VARCHAR(2048)", length = 2048, nullable = true, comment = "API Key")
    @TableField("api_key")
    private String apiKey;

    @JdbcColumn(name = "enabled", dataType = "BOOLEAN", nullable = false, defaultValue = "TRUE", comment = "启用状态")
    @TableField("enabled")
    private Boolean enabled;

    @JdbcColumn(name = "ext_json", dataType = "JSON", nullable = true, comment = "扩展配置JSON")
    @TableField(value = "ext_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extJson;
}

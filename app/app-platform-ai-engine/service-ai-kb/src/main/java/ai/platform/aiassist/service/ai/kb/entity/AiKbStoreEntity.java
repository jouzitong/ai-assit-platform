package ai.platform.aiassist.service.ai.kb.entity;

import ai.platform.aiassist.service.ai.api.enums.AiKbBizType;
import ai.platform.aiassist.service.ai.api.enums.AiKbStoreStatus;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 本地知识库主实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_kb_store", autoResultMap = true)
public class AiKbStoreEntity extends AuditableEntity {

    /** 本地知识库编码。 */
    @TableField("kb_code")
    private String kbCode;

    /** 知识库名称。 */
    @TableField("kb_name")
    private String kbName;

    /** 业务类型，例如 DB_DATA_SOURCE。 */
    @TableField("biz_type")
    private AiKbBizType bizType;

    /** 业务唯一键，例如 sourceKey。 */
    @TableField("biz_key")
    private String bizKey;

    /** Provider 编码，例如 qwen。 */
    @TableField("provider_code")
    private String providerCode;

    /** AI 侧真实知识库 ID。 */
    @TableField("provider_kb_id")
    private String providerKbId;

    /** 当前生效版本 ID。 */
    @TableField("current_version_id")
    private Long currentVersionId;

    /** 当前生效版本号。 */
    @TableField("current_version_no")
    private Integer currentVersionNo;

    /** 状态，例如 INIT、ACTIVE、SYNCING、FAILED、DISABLED。 */
    @TableField("status")
    private AiKbStoreStatus status;

    /** 是否启用。 */
    @TableField("enabled")
    private Boolean enabled;

    /** 知识库级可配置参数。 */
    @TableField(value = "config_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> configJson;

    /** 扩展信息。 */
    @TableField(value = "ext_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extJson;

    /** 最近一次发布时间。 */
    @TableField("last_publish_at")
    private LocalDateTime lastPublishAt;

    /** 备注。 */
    @TableField("remark")
    private String remark;
}

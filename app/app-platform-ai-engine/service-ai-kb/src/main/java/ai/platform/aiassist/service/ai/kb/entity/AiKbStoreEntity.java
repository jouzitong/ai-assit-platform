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
import org.athena.framework.data.mybatis.handler.DefaultEnumTypeHandler;
import org.athena.framework.data.mybatis.entity.AuditableEntity;

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

    /**
     * 本地知识库编码。
     *
     * @see #providerKbId 与 kbCode 相同
     */
    @TableField("kb_code")
    private String kbCode;

    /**
     * 知识库名称。
     */
    @TableField("kb_name")
    private String kbName;

    /**
     * 业务类型，例如 DB_DATA_SOURCE。
     */
    @TableField(value = "biz_type", typeHandler = DefaultEnumTypeHandler.class)
    private AiKbBizType bizType;

    /**
     * AI 侧真实知识库 ID。
     */
    @TableField("provider_kb_id")
    private String providerKbId;

    /**
     * 状态，例如 INIT、ACTIVE、SYNCING、FAILED、DISABLED。
     */
    @TableField(value = "status", typeHandler = DefaultEnumTypeHandler.class)
    private AiKbStoreStatus status;

    /**
     * 扩展信息。
     */
    @TableField(value = "ext_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> extJson;
}

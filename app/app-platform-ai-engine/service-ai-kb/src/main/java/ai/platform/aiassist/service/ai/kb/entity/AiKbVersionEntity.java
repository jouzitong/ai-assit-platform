package ai.platform.aiassist.service.ai.kb.entity;

import ai.platform.aiassist.service.ai.api.enums.AiKbProviderSyncStatus;
import ai.platform.aiassist.service.ai.api.enums.AiKbPublishType;
import ai.platform.aiassist.service.ai.api.enums.AiKbVersionStatus;
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
 * 知识库发布版本实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_kb_version", autoResultMap = true)
public class AiKbVersionEntity extends AuditableEntity {

    /** 所属知识库编码。 */
    @TableField("kb_code")
    private String kbCode;

    /** 版本号。 */
    @TableField("version_no")
    private Integer versionNo;

    /** 版本名称。 */
    @TableField("version_name")
    private String versionName;

    /** 版本状态，例如 DRAFT、CONFIRMED、PUBLISHING、PUBLISHED、FAILED、ROLLED_BACK。 */
    @TableField("status")
    private AiKbVersionStatus status;

    /** 发布类型，例如 MANUAL、ROLLBACK。 */
    @TableField("publish_type")
    private AiKbPublishType publishType;

    /** 本次发布时选中的文档与来源快照。 */
    @TableField(value = "source_snapshot_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> sourceSnapshotJson;

    /** 版本摘要信息，例如新增/修改/删除统计。 */
    @TableField(value = "summary_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> summaryJson;

    /** AI 侧同步状态。 */
    @TableField("provider_sync_status")
    private AiKbProviderSyncStatus providerSyncStatus;

    /** AI 侧同步时间。 */
    @TableField("provider_sync_at")
    private LocalDateTime providerSyncAt;

    /** AI 侧同步结果回执。 */
    @TableField(value = "provider_sync_result_json", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> providerSyncResultJson;

    /** 草稿创建人标识。 */
    @TableField("draft_created_by")
    private String draftCreatedBy;

    /** 发布人。 */
    @TableField("published_by")
    private String publishedBy;

    /** 发布时间。 */
    @TableField("published_at")
    private LocalDateTime publishedAt;

    /** 如果是回滚版本，记录来源版本 ID。 */
    @TableField("rollback_from_version_id")
    private Long rollbackFromVersionId;

    /** 备注。 */
    @TableField("remark")
    private String remark;
}

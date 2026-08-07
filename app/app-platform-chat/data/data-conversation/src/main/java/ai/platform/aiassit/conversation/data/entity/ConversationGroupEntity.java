package ai.platform.aiassit.conversation.data.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;
import org.athena.framework.data.mybatis.entity.LogicalDeleteEntity;

/**
 * 用户的聊天会话分组实体。
 *
 * <p>分组只负责组织会话，一期不承载提示词、文件或其他上下文内容。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("conversation_group")
public class ConversationGroupEntity extends LogicalDeleteEntity {

    /** 稳定的分组业务编码，供前端路由和会话关联使用。 */
    @JdbcColumn(
            name = "group_code",
            dataType = "VARCHAR(64)",
            length = 64,
            nullable = false,
            unique = true,
            comment = "分组编码"
    )
    @TableField("group_code")
    private String groupCode;

    /** 分组所有者。 */
    @JdbcColumn(
            name = "user_id",
            dataType = "BIGINT",
            nullable = false,
            defaultValue = "0",
            comment = "分组所属用户ID"
    )
    @TableField("user_id")
    private Long userId;

    /** 分组展示名称。 */
    @JdbcColumn(
            name = "group_name",
            dataType = "VARCHAR(128)",
            length = 128,
            nullable = false,
            comment = "分组名称"
    )
    @TableField("group_name")
    private String groupName;
}

package ai.platform.aiassit.render.data.render.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;

/**
 * 渲染页面当前内容实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("render_page_content")
public class RenderPageContentEntity extends AuditableEntity {

    /** 页面编码。 */
    @TableField("page_code")
    private String pageCode;

    /** 当前页面 JSON 内容。 */
    @TableField("content")
    private String content;
}

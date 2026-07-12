package ai.platform.aiassit.render.data.render.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.athena.framework.data.mybatis.entity.AuditableEntity;
import org.athena.framework.data.jdbc.annotations.JdbcColumn;

import java.util.Map;

/**
 * 渲染页面当前内容实体。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "render_page_content", autoResultMap = true)
public class RenderPageContentEntity extends AuditableEntity {

    /** 页面编码。 */
    @JdbcColumn(name = "page_code", unique = true, comment = "页面编码。")
    @TableField("page_code")
    private String pageCode;

    /** 当前页面 JSON 内容。 */
    @JdbcColumn(name = "content", comment = "当前页面 JSON 内容。")
    @TableField(value = "content", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> content;
}

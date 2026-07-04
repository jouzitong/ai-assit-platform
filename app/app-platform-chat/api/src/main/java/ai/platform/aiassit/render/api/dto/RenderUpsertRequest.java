package ai.platform.aiassit.render.api.dto;

import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import lombok.Data;
import lombok.ToString;

/**
 * 渲染页面新增或更新请求。
 */
@Data
@ToString(exclude = {"content"})
public class RenderUpsertRequest {

    /**
     * 页面编码。
     */
    private String code;

    /**
     * 页面名称。
     */
    private String name;

    /**
     * 所属分类编码。
     */
    private String categoryCode;

    /**
     * 页面状态。
     */
    private EffectiveStatus status;

    /**
     * 页面内容。
     */
    private String content;

    public long getContentLength() {
        return content == null ? 0 : content.length();
    }

}

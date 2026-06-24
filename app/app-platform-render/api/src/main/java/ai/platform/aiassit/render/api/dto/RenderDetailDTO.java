package ai.platform.aiassit.render.api.dto;

import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import lombok.Data;

/**
 * 渲染页面详情。
 */
@Data
public class RenderDetailDTO {

    /** 页面编码。 */
    private String code;

    /** 页面名称。 */
    private String name;

    /** 所属分类编码。 */
    private String categoryCode;

    /** 页面状态。 */
    private EffectiveStatus status;

    /** 当前页面内容。 */
    private String content;
}

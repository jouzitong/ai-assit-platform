package ai.platform.aiassit.render.api.dto;

import lombok.Data;

/**
 * 渲染页面获取请求。
 */
@Data
public class RenderGetRequest {

    /** 页面编码。 */
    private String code;
}

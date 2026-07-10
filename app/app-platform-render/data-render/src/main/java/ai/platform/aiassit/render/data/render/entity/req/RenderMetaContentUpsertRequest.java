package ai.platform.aiassit.render.data.render.entity.req;

import lombok.Data;

import java.util.Map;

/**
 * Render 元数据内容保存请求。
 */
@Data
public class RenderMetaContentUpsertRequest {

    /** 页面编码。 */
    private String code;

    /** Render JSON 内容。 */
    private Map<String, Object> content;
}

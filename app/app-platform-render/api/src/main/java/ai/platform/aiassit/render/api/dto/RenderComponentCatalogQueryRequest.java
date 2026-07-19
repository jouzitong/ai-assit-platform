package ai.platform.aiassit.render.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 已发布 Render 组件目录查询条件。
 */
@Data
public class RenderComponentCatalogQueryRequest {

    /** 需要精确查询的稳定组件 key；为空时不按 key 过滤。 */
    private List<String> componentKeys = new ArrayList<>();

    /** 组件分类；为空时不按分类过滤。 */
    private String category;

    /** 匹配组件 key、名称、分类或说明文档的关键词。 */
    private String keyword;

    /** 返回数量；服务端始终限制在 100 以内。 */
    private Integer limit;
}

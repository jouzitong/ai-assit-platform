package ai.platform.aiassit.render.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 已发布 Render 组件目录查询结果。
 */
@Data
public class RenderComponentCatalogResponse {

    /** 本次查询命中的已发布组件。 */
    private List<RenderComponentCatalogComponentDTO> components = new ArrayList<>();

    /** 全量已发布组件目录的 SHA-256 revision。 */
    private String catalogRevision;
}

package ai.platform.aiassit.render.api.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Render 组件目录中的一个已发布组件事实。
 */
@Data
public class RenderComponentCatalogComponentDTO {

    /** 稳定组件 key。 */
    private String componentKey;

    /** 组件名称。 */
    private String name;

    /** 组件分类。 */
    private String category;

    /** component-asset/v1 示例中声明的源组件版本；未声明时为空。 */
    private String componentVersion;

    /** 当前组件事实与内容的 SHA-256 revision。 */
    private String sourceRevision;

    /** 当前组件说明 Markdown。 */
    private String docMarkdown;

    /** 当前组件示例 JSON 原文。 */
    private String exampleJson;

    /** 组件事实或内容的最近更新时间。 */
    private LocalDateTime updatedAt;
}

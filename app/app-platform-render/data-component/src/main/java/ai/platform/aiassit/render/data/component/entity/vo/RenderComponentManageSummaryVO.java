package ai.platform.aiassit.render.data.component.entity.vo;

import lombok.Data;

@Data
public class RenderComponentManageSummaryVO {

    private Long total;

    private Long published;

    private Long draft;

    private Long disabled;

    private Long categories;
}

package ai.platform.aiassit.render.data.component.entity.req;

import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import lombok.Data;

@Data
public class RenderComponentManageRequest {

    private String key;

    private String name;

    private String category;

    private EffectiveStatus status;

    private String docMarkdown;

    private String exampleJson;
}

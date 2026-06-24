package ai.platform.aiassit.render.data.render.entity.req;

import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import lombok.Data;

@Data
public class RenderPageManageRequest {

    private String code;

    private String name;

    private String categoryCode;

    private EffectiveStatus status;

    private String content;
}

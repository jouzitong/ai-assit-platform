package ai.platform.aiassit.render.data.component.entity.req;

import ai.platform.aiassit.render.api.enums.EffectiveStatus;
import lombok.Data;

@Data
public class RenderComponentStatusUpdateRequest {

    private EffectiveStatus status;
}

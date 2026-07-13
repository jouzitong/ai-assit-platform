package ai.platform.aiassit.data.virtualization.core.transform;

import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformPortEntity;

import java.util.List;
import java.util.Map;

public record TransformDefinition(
        String ruleCode,
        List<FieldTransformPortEntity> physicalPorts,
        List<FieldTransformPortEntity> virtualPorts,
        Map<String, Object> config
) {
}

package ai.platform.aiassit.data.virtualization.core.transform;

import ai.platform.aiassit.data.virtualization.core.catalog.CatalogSnapshot;
import ai.platform.aiassit.data.virtualization.data.entity.FieldTransformPortEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将变换器输出稳定映射到声明的目标端口。 */
public final class TransformOutputMapper {
    private TransformOutputMapper() {
    }

    public static Map<String, Object> normalizeSnapshot(Map<String, Object> output, List<CatalogSnapshot.Port> targets) {
        return normalize(output, targets.stream().map(CatalogSnapshot.Port::code).toList());
    }

    public static Map<String, Object> normalizeEntity(Map<String, Object> output, List<FieldTransformPortEntity> targets) {
        return normalize(output, targets.stream().map(FieldTransformPortEntity::getPortCode).toList());
    }

    private static Map<String, Object> normalize(Map<String, Object> output, List<String> targetCodes) {
        Map<String, Object> safeOutput = output == null ? Map.of() : output;
        Map<String, Object> normalized = new LinkedHashMap<>();
        if (targetCodes.stream().allMatch(safeOutput::containsKey)) {
            targetCodes.forEach(code -> normalized.put(code, safeOutput.get(code)));
            return normalized;
        }
        List<Object> values = new ArrayList<>(safeOutput.values());
        if (values.size() != targetCodes.size()) {
            throw new ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException(
                    "FIELD_TRANSFORM_INVALID", "变换器输出数量与目标端口不一致");
        }
        for (int i = 0; i < targetCodes.size(); i++) {
            normalized.put(targetCodes.get(i), values.get(i));
        }
        return normalized;
    }
}

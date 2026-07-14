package ai.platform.aiassit.data.virtualization.core.plan;

import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilter;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilterOperator;
import ai.platform.aiassit.data.virtualization.spi.model.PhysicalFilterType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Converts a validated virtual filter tree into physical-column semantics without rendering SQL. */
@Component
public class PhysicalFilterMapper {

    public boolean canMap(FilterNode node, Map<String, String> fields) {
        if (node == null) return true;
        if (node.getType() == FilterType.PREDICATE) return fields.containsKey(node.getField());
        return node.getChildren() != null && node.getChildren().stream().allMatch(child -> canMap(child, fields));
    }

    public PhysicalFilter map(FilterNode node, Map<String, String> fields) {
        if (node == null) return null;
        validate(node);
        return mapValidated(node, fields);
    }

    private PhysicalFilter mapValidated(FilterNode node, Map<String, String> fields) {
        if (node.getType() == FilterType.PREDICATE) {
            String physicalField = fields.get(node.getField());
            if (physicalField == null) {
                throw new VirtualDataException("FIELD_TRANSFORM_PUSHDOWN_UNSUPPORTED",
                        "字段不能安全映射到物理过滤条件: " + node.getField());
            }
            List<Object> values = normalizedValues(node);
            if ((node.getOperator() == ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator.IN
                    || node.getOperator() == ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator.NOT_IN)
                    && values.isEmpty()) {
                throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "IN 条件不能为空");
            }
            return new PhysicalFilter(
                    PhysicalFilterType.PREDICATE,
                    physicalField,
                    PhysicalFilterOperator.valueOf(node.getOperator().name()),
                    node.getValue(),
                    values,
                    List.of()
            );
        }
        List<PhysicalFilter> children = node.getChildren() == null ? List.of()
                : node.getChildren().stream().map(child -> mapValidated(child, fields)).toList();
        return new PhysicalFilter(
                PhysicalFilterType.valueOf(node.getType().name()),
                null,
                null,
                null,
                List.of(),
                children
        );
    }

    private void validate(FilterNode node) {
        if (node.getType() == null) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "过滤节点缺少 type");
        }
        if (node.getType() == FilterType.PREDICATE) {
            if (node.getField() == null || node.getField().isBlank() || node.getOperator() == null) {
                throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "过滤谓词缺少 field/operator");
            }
            return;
        }
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "逻辑过滤节点缺少 children");
        }
        if (node.getType() == FilterType.NOT && node.getChildren().size() != 1) {
            throw new VirtualDataException("FIELD_TRANSFORM_INVALID", "NOT 过滤节点必须且只能有一个 child");
        }
        node.getChildren().forEach(this::validate);
    }

    private List<Object> normalizedValues(FilterNode node) {
        if (node.getValues() != null && !node.getValues().isEmpty()) {
            return new ArrayList<>(node.getValues());
        }
        if (node.getValue() instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        return List.of();
    }
}

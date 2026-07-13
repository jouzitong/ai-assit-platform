package ai.platform.aiassit.data.virtualization.api.dto;

import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 无字符串表达式的强类型过滤树。 */
@Data
public class FilterNode {
    private FilterType type;
    private String field;
    private FilterOperator operator;
    private Object value;
    private List<Object> values = new ArrayList<>();
    private List<FilterNode> children = new ArrayList<>();
}

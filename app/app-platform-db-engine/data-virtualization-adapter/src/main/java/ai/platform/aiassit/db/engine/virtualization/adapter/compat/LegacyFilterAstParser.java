package ai.platform.aiassit.db.engine.virtualization.adapter.compat;

import ai.platform.aiassit.data.virtualization.api.dto.FilterNode;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterOperator;
import ai.platform.aiassit.data.virtualization.api.enums.VirtualDataEnums.FilterType;
import ai.platform.aiassit.db.engine.api.dto.DbQueryFilterCondition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 将旧 filter_dict/filterExpr 安全转换为虚拟查询过滤树。 */
public class LegacyFilterAstParser {

    static final String INVALID_FILTER = "LEGACY_FILTER_INVALID";
    static final String UNSUPPORTED_OPERATOR = "LEGACY_FILTER_OPERATOR_UNSUPPORTED";
    static final String VIRTUAL_OPERATOR_MISSING = "LEGACY_FILTER_OPERATOR_UNSUPPORTED_BY_VIRTUAL_API";

    /**
     * 解析旧过滤协议。
     *
     * <p>未提供表达式时条件按 AND 连接；提供表达式时保留旧实现的 AND 高于 OR、括号和
     * “必须恰好引用全部 filter_dict key”语义。</p>
     */
    public FilterNode parse(Map<String, ?> filterDict, String filterExpr) {
        Map<String, ?> filters = filterDict == null ? Map.of() : filterDict;
        if (filters.isEmpty()) {
            if (hasText(filterExpr)) {
                throw invalid("filterExpr 存在时 filter_dict 不能为空");
            }
            return null;
        }

        Map<String, FilterNode> predicates = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : filters.entrySet()) {
            String key = requireIdentifier(entry.getKey(), "filter key");
            predicates.put(key, predicate(key, entry.getValue()));
        }

        if (!hasText(filterExpr)) {
            return combine(FilterType.AND, new ArrayList<>(predicates.values()));
        }
        return new ExpressionParser(filterExpr, predicates).parse();
    }

    private FilterNode predicate(String field, Object rawCondition) {
        NormalizedCondition condition = normalize(rawCondition);
        FilterOperator operator = operator(condition.operator());
        Object value = condition.value();

        FilterNode node = new FilterNode();
        node.setType(FilterType.PREDICATE);
        node.setField(field);
        node.setOperator(operator);
        if (operator == FilterOperator.IN || operator == FilterOperator.NOT_IN) {
            if (!(value instanceof Collection<?> collection) || collection.isEmpty()) {
                throw invalid(operator + " 条件必须提供非空集合: " + field);
            }
            node.setValue(value);
            node.setValues(new ArrayList<>(collection));
        } else if (operator != FilterOperator.IS_NULL && operator != FilterOperator.IS_NOT_NULL) {
            node.setValue(value);
        }
        return node;
    }

    private NormalizedCondition normalize(Object rawCondition) {
        if (rawCondition instanceof DbQueryFilterCondition condition) {
            return new NormalizedCondition(defaultOperator(condition.getOp()), condition.getValue());
        }
        if (rawCondition instanceof Map<?, ?> map && (map.containsKey("op") || map.containsKey("value"))) {
            Object rawOperator = map.get("op");
            return new NormalizedCondition(defaultOperator(rawOperator == null ? null : String.valueOf(rawOperator)), map.get("value"));
        }
        return new NormalizedCondition("eq", rawCondition);
    }

    private FilterOperator operator(String operator) {
        return switch (operator.toLowerCase(Locale.ROOT)) {
            case "eq" -> FilterOperator.EQ;
            case "ne", "neq" -> FilterOperator.NE;
            case "gt" -> FilterOperator.GT;
            case "gte", "ge" -> FilterOperator.GTE;
            case "lt" -> FilterOperator.LT;
            case "lte", "le" -> FilterOperator.LTE;
            case "like" -> FilterOperator.LIKE;
            case "prefix_like" -> extendedOperator("STARTS_WITH");
            case "suffix_like" -> extendedOperator("ENDS_WITH");
            case "in" -> FilterOperator.IN;
            case "not_in" -> FilterOperator.NOT_IN;
            case "is_null" -> FilterOperator.IS_NULL;
            case "is_not_null" -> FilterOperator.IS_NOT_NULL;
            default -> throw new LegacyQueryCompatibilityException(
                    UNSUPPORTED_OPERATOR,
                    "不支持的旧查询操作符: " + operator
            );
        };
    }

    private FilterOperator extendedOperator(String name) {
        try {
            return FilterOperator.valueOf(name);
        } catch (IllegalArgumentException exception) {
            throw new LegacyQueryCompatibilityException(
                    VIRTUAL_OPERATOR_MISSING,
                    "虚拟过滤协议尚未提供操作符: " + name
            );
        }
    }

    private FilterNode combine(FilterType type, List<FilterNode> children) {
        if (children.size() == 1) {
            return children.get(0);
        }
        FilterNode node = new FilterNode();
        node.setType(type);
        node.setChildren(new ArrayList<>(children));
        return node;
    }

    private String requireIdentifier(String value, String label) {
        if (!hasText(value)) {
            throw invalid(label + " 不能为空");
        }
        String trimmed = value.trim();
        if (!trimmed.matches("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")) {
            throw invalid(label + " 格式非法: " + value);
        }
        return trimmed;
    }

    private String defaultOperator(String operator) {
        return hasText(operator) ? operator.trim() : "eq";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private LegacyQueryCompatibilityException invalid(String message) {
        return new LegacyQueryCompatibilityException(INVALID_FILTER, message);
    }

    private record NormalizedCondition(String operator, Object value) {
    }

    private final class ExpressionParser {

        private final List<String> tokens;
        private final Map<String, FilterNode> predicates;
        private final Set<String> referencedIdentifiers = new LinkedHashSet<>();
        private int index;

        private ExpressionParser(String expression, Map<String, FilterNode> predicates) {
            this.tokens = tokenize(expression);
            this.predicates = predicates;
        }

        private FilterNode parse() {
            FilterNode result = parseOrExpression();
            if (index != tokens.size()) {
                throw invalid("filterExpr 包含无法解析的剩余内容");
            }
            if (!predicates.keySet().equals(referencedIdentifiers)) {
                throw invalid("filterExpr 必须恰好引用 filter_dict 中的全部 key");
            }
            return result;
        }

        private FilterNode parseOrExpression() {
            List<FilterNode> children = new ArrayList<>();
            children.add(parseAndExpression());
            while (matchKeyword("or")) {
                children.add(parseAndExpression());
            }
            return combine(FilterType.OR, children);
        }

        private FilterNode parseAndExpression() {
            List<FilterNode> children = new ArrayList<>();
            children.add(parsePrimaryExpression());
            while (matchKeyword("and")) {
                children.add(parsePrimaryExpression());
            }
            return combine(FilterType.AND, children);
        }

        private FilterNode parsePrimaryExpression() {
            if (matchToken("(")) {
                FilterNode nested = parseOrExpression();
                if (!matchToken(")")) {
                    throw invalid("filterExpr 括号不匹配");
                }
                return nested;
            }
            String identifier = consumeIdentifier();
            referencedIdentifiers.add(identifier);
            FilterNode predicate = predicates.get(identifier);
            if (predicate == null) {
                throw invalid("filterExpr 引用了不存在的 filter key: " + identifier);
            }
            return predicate;
        }

        private boolean matchKeyword(String keyword) {
            if (index >= tokens.size() || !keyword.equalsIgnoreCase(tokens.get(index))) {
                return false;
            }
            index++;
            return true;
        }

        private boolean matchToken(String token) {
            if (index >= tokens.size() || !token.equals(tokens.get(index))) {
                return false;
            }
            index++;
            return true;
        }

        private String consumeIdentifier() {
            if (index >= tokens.size()) {
                throw invalid("filterExpr 缺少条件 key");
            }
            String token = tokens.get(index++);
            if ("(".equals(token) || ")".equals(token)
                    || "and".equalsIgnoreCase(token) || "or".equalsIgnoreCase(token)) {
                throw invalid("filterExpr 条件 key 非法: " + token);
            }
            return token;
        }

        private List<String> tokenize(String expression) {
            if (!hasText(expression)) {
                throw invalid("filterExpr 不能为空");
            }
            List<String> result = new ArrayList<>();
            int position = 0;
            while (position < expression.length()) {
                char current = expression.charAt(position);
                if (Character.isWhitespace(current)) {
                    position++;
                    continue;
                }
                if (current == '(' || current == ')') {
                    result.add(String.valueOf(current));
                    position++;
                    continue;
                }
                int start = position;
                while (position < expression.length()) {
                    char value = expression.charAt(position);
                    if (Character.isLetterOrDigit(value) || value == '_' || value == '.') {
                        position++;
                    } else {
                        break;
                    }
                }
                if (start == position) {
                    throw invalid("filterExpr 包含非法字符: " + current);
                }
                result.add(expression.substring(start, position));
            }
            return result;
        }
    }
}

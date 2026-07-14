package ai.platform.aiassit.data.virtualization.core.transform.builtin;

import ai.platform.aiassit.data.virtualization.core.exception.VirtualDataException;
import ai.platform.aiassit.data.virtualization.core.transform.FieldTransformer;
import ai.platform.aiassit.data.virtualization.core.transform.TransformDefinition;
import ai.platform.aiassit.data.virtualization.core.transform.TransformerCapabilities;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A deliberately small, deterministic Python-like transform language.
 *
 * <p>This is not an embedded Python runtime. It only supports the constructs
 * documented by the virtual-table editor and cannot access the JVM, files,
 * network, processes, environment variables, or Spring beans.</p>
 */
@Component
public class PythonLikeFieldTransformer implements FieldTransformer {
    private static final String SCRIPT_KEY = "__scriptCode";
    private static final String DIRECTION_KEY = "__direction";
    private static final int MAX_SCRIPT_LENGTH = 20_000;
    private static final int MAX_OUTPUT_ENTRIES = 128;
    private final Map<String, Program> programs = new ConcurrentHashMap<>();

    @Override
    public String code() {
        return "script";
    }

    @Override
    public int version() {
        return 1;
    }

    @Override
    public TransformerCapabilities capabilities() {
        return new TransformerCapabilities(true, true, true, false, false, false, true);
    }

    @Override
    public void validate(TransformDefinition definition) {
        Program program = program(definition.config());
        String direction = String.valueOf(definition.config().getOrDefault(DIRECTION_KEY, ""));
        if (!"read".equals(direction) && !"write".equals(direction)) {
            throw invalid("脚本规则缺少转换方向");
        }
        if (!program.methods().containsKey(direction)) {
            throw invalid("脚本必须定义 def " + direction + "(inputs, context):");
        }
        if (definition.physicalPorts().isEmpty() || definition.virtualPorts().isEmpty()) {
            throw invalid("脚本规则必须至少配置一个物理字段和一个虚拟字段");
        }
    }

    @Override
    public Map<String, Object> read(Map<String, Object> physicalPorts, Map<String, Object> config) {
        return execute(config, "read", physicalPorts);
    }

    @Override
    public Map<String, Object> write(Map<String, Object> virtualPorts, Map<String, Object> config) {
        return execute(config, "write", virtualPorts);
    }

    private Map<String, Object> execute(Map<String, Object> config, String method, Map<String, Object> inputs) {
        Program program = program(config);
        if (!program.methods().containsKey(method)) {
            throw invalid("脚本未定义 " + method + " 方法");
        }
        Map<String, String> inputAliases = aliases(config, "__inputAliases");
        Map<String, Object> aliasedInputs = new LinkedHashMap<>();
        if (inputs != null) {
            inputs.forEach((key, item) -> aliasedInputs.put(inputAliases.getOrDefault(key, key), item));
        }
        Map<String, Object> safeInputs = Collections.unmodifiableMap(aliasedInputs);
        Map<String, Object> context = Map.of("direction", method);
        Object value;
        try {
            value = program.execute(method, safeInputs, context);
        }
        catch (VirtualDataException error) {
            throw error;
        }
        catch (RuntimeException error) {
            throw invalid("脚本执行失败: " + safeMessage(error));
        }
        if (!(value instanceof Map<?, ?> raw)) {
            throw invalid(method + " 必须返回对象，例如 {\"field\": value}");
        }
        if (raw.size() > MAX_OUTPUT_ENTRIES) {
            throw invalid("脚本输出字段数量不能超过 " + MAX_OUTPUT_ENTRIES);
        }
        Map<String, String> outputAliases = aliases(config, "__outputAliases");
        Map<String, String> outputPortsByAlias = new LinkedHashMap<>();
        outputAliases.forEach((port, alias) -> outputPortsByAlias.put(alias, port));
        Map<String, Object> output = new LinkedHashMap<>();
        raw.forEach((key, item) -> {
            if (!(key instanceof String) || key.toString().isBlank()) {
                throw invalid("脚本输出键必须是非空字符串");
            }
            output.put(outputPortsByAlias.getOrDefault(key.toString(), key.toString()), item);
        });
        return output;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> aliases(Map<String, Object> config, String key) {
        Object value = config == null ? null : config.get(key);
        if (!(value instanceof Map<?, ?> raw)) return Map.of();
        Map<String, String> aliases = new LinkedHashMap<>();
        raw.forEach((left, right) -> aliases.put(String.valueOf(left), String.valueOf(right)));
        return aliases;
    }

    private Program program(Map<String, Object> config) {
        String source = config == null ? null : String.valueOf(config.getOrDefault(SCRIPT_KEY, ""));
        if (source == null || source.isBlank()) throw invalid("转换脚本不能为空");
        if (source.length() > MAX_SCRIPT_LENGTH) throw invalid("转换脚本不能超过 " + MAX_SCRIPT_LENGTH + " 个字符");
        try {
            return programs.computeIfAbsent(source, Program::parse);
        }
        catch (RuntimeException error) {
            throw error instanceof VirtualDataException ? error : invalid("脚本语法错误: " + safeMessage(error));
        }
    }

    private static VirtualDataException invalid(String message) {
        return new VirtualDataException("FIELD_TRANSFORM_INVALID", message);
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private record SourceLine(int indent, String text) {
    }

    private record Method(List<SourceLine> lines) {
    }

    private record Program(Map<String, Method> methods) {
        static Program parse(String source) {
            List<SourceLine> lines = new ArrayList<>();
            for (String raw : source.replace("\r", "").split("\n", -1)) {
                String text = raw.stripTrailing();
                if (text.isBlank() || text.stripLeading().startsWith("#")) continue;
                int indent = 0;
                while (indent < text.length() && text.charAt(indent) == ' ') indent++;
                if (text.indexOf('\t') >= 0) throw invalid("脚本只支持空格缩进");
                lines.add(new SourceLine(indent, text.substring(indent)));
            }
            Map<String, Method> methods = new LinkedHashMap<>();
            for (int i = 0; i < lines.size(); i++) {
                SourceLine line = lines.get(i);
                if (line.indent() != 0) throw invalid("方法定义必须顶格书写");
                String name = methodName(line.text());
                if (name == null) throw invalid("只支持 def read(inputs, context): 和 def write(inputs, context):");
                if (methods.containsKey(name)) throw invalid("方法重复定义: " + name);
                int end = i + 1;
                while (end < lines.size() && lines.get(end).indent() > 0) end++;
                if (end == i + 1) throw invalid(name + " 方法不能为空");
                int methodIndent = minimumIndent(lines, i + 1, end);
                List<SourceLine> body = lines.subList(i + 1, end).stream()
                        .map(item -> new SourceLine(item.indent() - methodIndent, item.text()))
                        .toList();
                validateBlock(body, 0, body.size(), baseIndent(body));
                methods.put(name, new Method(body));
                i = end - 1;
            }
            if (methods.isEmpty()) throw invalid("至少定义一个 read 或 write 方法");
            return new Program(Map.copyOf(methods));
        }

        Object execute(String name, Map<String, Object> inputs, Map<String, Object> context) {
            Map<String, Object> variables = new LinkedHashMap<>();
            variables.put("inputs", inputs);
            variables.put("context", context);
            ExecResult result = executeBlock(methods.get(name).lines(), 0, methods.get(name).lines().size(), baseIndent(methods.get(name).lines()), variables);
            if (!result.returned()) throw invalid(name + " 方法必须 return 一个对象");
            return result.value();
        }

        private static ExecResult executeBlock(List<SourceLine> lines, int start, int end, int indent, Map<String, Object> variables) {
            int i = start;
            while (i < end) {
                SourceLine line = lines.get(i);
                if (line.indent() < indent) break;
                if (line.indent() > indent) throw invalid("缩进层级不合法: " + line.text());
                if (line.text().startsWith("if ") && line.text().endsWith(":")) {
                    String condition = line.text().substring(3, line.text().length() - 1).trim();
                    int bodyStart = i + 1;
                    int bodyEnd = childEnd(lines, bodyStart, end, indent);
                    boolean matched = truthy(Expression.parse(condition).evaluate(variables));
                    if (matched) {
                        ExecResult result = executeBlock(lines, bodyStart, bodyEnd, childIndent(lines, bodyStart, bodyEnd, indent), variables);
                        if (result.returned()) return result;
                    }
                    i = bodyEnd;
                    if (i < end && lines.get(i).indent() == indent && "else:".equals(lines.get(i).text())) {
                        int elseStart = i + 1;
                        int elseEnd = childEnd(lines, elseStart, end, indent);
                        if (!matched) {
                            ExecResult result = executeBlock(lines, elseStart, elseEnd, childIndent(lines, elseStart, elseEnd, indent), variables);
                            if (result.returned()) return result;
                        }
                        i = elseEnd;
                    }
                    continue;
                }
                String statement = collectStatement(lines, i, end);
                if (statement.startsWith("return ")) {
                    return new ExecResult(true, Expression.parse(statement.substring(7).trim()).evaluate(variables));
                }
                if (statement.startsWith("raise ValueError(")) {
                    if (!statement.endsWith(")")) throw invalid("ValueError 语法不完整");
                    Object message = Expression.parse(statement.substring(17, statement.length() - 1).trim()).evaluate(variables);
                    throw invalid(String.valueOf(message));
                }
                int equals = assignmentIndex(statement);
                if (equals > 0) {
                    String variable = statement.substring(0, equals).trim();
                    if (!variable.matches("[A-Za-z_][A-Za-z0-9_]*")) throw invalid("变量名不合法: " + variable);
                    variables.put(variable, Expression.parse(statement.substring(equals + 1).trim()).evaluate(variables));
                    i += statementLineCount(lines, i, end);
                    continue;
                }
                throw invalid("不支持的脚本语句: " + statement);
            }
            return new ExecResult(false, null);
        }

        private static String collectStatement(List<SourceLine> lines, int index, int end) {
            StringBuilder value = new StringBuilder(lines.get(index).text());
            int balance = bracketBalance(value.toString());
            int cursor = index + 1;
            while (balance > 0 && cursor < end) {
                value.append(' ').append(lines.get(cursor).text().trim());
                balance += bracketBalance(lines.get(cursor).text());
                cursor++;
            }
            if (balance != 0) throw invalid("括号未闭合: " + value);
            return value.toString().trim();
        }

        private static int statementLineCount(List<SourceLine> lines, int index, int end) {
            int balance = bracketBalance(lines.get(index).text());
            int cursor = index + 1;
            while (balance > 0 && cursor < end) {
                balance += bracketBalance(lines.get(cursor).text());
                cursor++;
            }
            return cursor - index;
        }

        private static int assignmentIndex(String statement) {
            int balance = 0;
            for (int i = 0; i < statement.length(); i++) {
                char ch = statement.charAt(i);
                if (ch == '[' || ch == '{' || ch == '(') balance++;
                if (ch == ']' || ch == '}' || ch == ')') balance--;
                if (ch == '=' && balance == 0 && (i + 1 >= statement.length() || statement.charAt(i + 1) != '=')) return i;
            }
            return -1;
        }

        private static int bracketBalance(String value) {
            int balance = 0;
            boolean quoted = false;
            char quote = 0;
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if ((ch == '\'' || ch == '"') && (i == 0 || value.charAt(i - 1) != '\\')) {
                    if (!quoted) { quoted = true; quote = ch; }
                    else if (quote == ch) quoted = false;
                }
                if (quoted) continue;
                if (ch == '[' || ch == '{' || ch == '(') balance++;
                if (ch == ']' || ch == '}' || ch == ')') balance--;
            }
            return balance;
        }

        private static void validateBlock(List<SourceLine> lines, int start, int end, int indent) {
            int i = start;
            while (i < end) {
                SourceLine line = lines.get(i);
                if (line.indent() < indent) break;
                if (line.indent() > indent) throw invalid("缩进层级不合法: " + line.text());
                if (line.text().startsWith("if ") && line.text().endsWith(":")) {
                    int bodyStart = i + 1;
                    int bodyEnd = childEnd(lines, bodyStart, end, indent);
                    validateExpression(line.text().substring(3, line.text().length() - 1).trim());
                    validateBlock(lines, bodyStart, bodyEnd, childIndent(lines, bodyStart, bodyEnd, indent));
                    i = bodyEnd;
                    if (i < end && lines.get(i).indent() == indent && "else:".equals(lines.get(i).text())) {
                        int elseStart = i + 1;
                        int elseEnd = childEnd(lines, elseStart, end, indent);
                        validateBlock(lines, elseStart, elseEnd, childIndent(lines, elseStart, elseEnd, indent));
                        i = elseEnd;
                    }
                    continue;
                }
                String statement = collectStatement(lines, i, end);
                if (statement.startsWith("return ")) validateExpression(statement.substring(7).trim());
                else if (statement.startsWith("raise ValueError(")) validateExpression(statement.substring(17, statement.length() - 1).trim());
                else if (assignmentIndex(statement) > 0) validateExpression(statement.substring(assignmentIndex(statement) + 1).trim());
                else throw invalid("不支持的脚本语句: " + statement);
                i += statementLineCount(lines, i, end);
            }
        }

        private static void validateExpression(String expression) {
            Expression.parse(expression);
        }

        private static int childEnd(List<SourceLine> lines, int start, int end, int parentIndent) {
            int i = start;
            while (i < end && lines.get(i).indent() > parentIndent) i++;
            return i;
        }

        private static int childIndent(List<SourceLine> lines, int start, int end, int parentIndent) {
            if (start >= end || lines.get(start).indent() <= parentIndent) throw invalid("控制语句必须包含缩进代码块");
            return lines.get(start).indent();
        }

        private static int minimumIndent(List<SourceLine> lines, int start, int end) {
            return baseIndent(lines.subList(start, end));
        }

        private static int baseIndent(List<SourceLine> lines) {
            return lines.stream().mapToInt(SourceLine::indent).min().orElse(0);
        }

        private static String methodName(String text) {
            if (text.matches("def read\\(\\s*inputs\\s*,\\s*context\\s*\\):")) return "read";
            if (text.matches("def write\\(\\s*inputs\\s*,\\s*context\\s*\\):")) return "write";
            return null;
        }
    }

    private record ExecResult(boolean returned, Object value) {
    }

    private static boolean truthy(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean item) return item;
        if (value instanceof Number item) return item.doubleValue() != 0;
        if (value instanceof Collection<?> item) return !item.isEmpty();
        return !String.valueOf(value).isEmpty();
    }

    private interface Node {
        Object evaluate(Map<String, Object> variables);
    }

    private static final class Expression {
        private final List<Token> tokens;
        private int position;

        private Expression(List<Token> tokens) {
            this.tokens = tokens;
        }

        static Expression parse(String source) {
            Expression expression = new Expression(new Lexer(source).tokens());
            expression.parseConditional();
            expression.expect(TokenType.END, "表达式后存在多余内容");
            expression.position = 0;
            return expression;
        }

        Object evaluate(Map<String, Object> variables) {
            position = 0;
            return parseConditional().evaluate(variables);
        }

        private Node parseConditional() {
            Node value = parseOr();
            if (matchWord("if")) {
                Node condition = parseOr();
                expectWord("else");
                Node alternative = parseConditional();
                return vars -> truthy(condition.evaluate(vars)) ? value.evaluate(vars) : alternative.evaluate(vars);
            }
            return value;
        }

        private Node parseOr() {
            Node left = parseAnd();
            while (matchWord("or")) {
                Node right = parseAnd();
                left = binary(left, right, (a, b) -> truthy(a) || truthy(b));
            }
            return left;
        }

        private Node parseAnd() {
            Node left = parseNot();
            while (matchWord("and")) {
                Node right = parseNot();
                left = binary(left, right, (a, b) -> truthy(a) && truthy(b));
            }
            return left;
        }

        private Node parseNot() {
            if (matchWord("not")) {
                Node value = parseNot();
                return vars -> !truthy(value.evaluate(vars));
            }
            return parseComparison();
        }

        private Node parseComparison() {
            Node left = parseAdditive();
            if (matchSymbol("==")) return binary(left, parseAdditive(), PythonLikeFieldTransformer::equalsValue);
            if (matchSymbol("!=")) return binary(left, parseAdditive(), (a, b) -> !equalsValue(a, b));
            if (matchSymbol(">")) return binary(left, parseAdditive(), (a, b) -> compare(a, b) > 0);
            if (matchSymbol(">=")) return binary(left, parseAdditive(), (a, b) -> compare(a, b) >= 0);
            if (matchSymbol("<")) return binary(left, parseAdditive(), (a, b) -> compare(a, b) < 0);
            if (matchSymbol("<=")) return binary(left, parseAdditive(), (a, b) -> compare(a, b) <= 0);
            if (matchWord("in")) return binary(left, parseAdditive(), PythonLikeFieldTransformer::contains);
            if (matchWord("not")) {
                expectWord("in");
                return binary(left, parseAdditive(), (a, b) -> !contains(a, b));
            }
            if (matchWord("is")) {
                boolean negated = matchWord("not");
                Node right = parseAdditive();
                return binary(left, right, (a, b) -> negated ? a != b : a == b);
            }
            return left;
        }

        private Node parseAdditive() {
            Node left = parseUnary();
            while (peekSymbol("+") || peekSymbol("-")) {
                boolean plus = matchSymbol("+");
                if (!plus) expectSymbol("-");
                Node right = parseUnary();
                left = binary(left, right, plus ? PythonLikeFieldTransformer::add : PythonLikeFieldTransformer::subtract);
            }
            return left;
        }

        private Node parseUnary() {
            if (matchSymbol("-")) {
                Node value = parseUnary();
                return vars -> -number(value.evaluate(vars)).doubleValue();
            }
            return parsePostfix();
        }

        private Node parsePostfix() {
            Node value = parsePrimary();
            while (true) {
                if (matchSymbol(".")) {
                    String method = expect(TokenType.WORD, "仅支持受限对象方法").text();
                    expectSymbol("(");
                    Node argument = parseConditional();
                    expectSymbol(")");
                    if (!"get".equals(method)) throw invalid("只支持 inputs.get(key) 或 context.get(key)");
                    Node target = value;
                    value = vars -> {
                        Object source = target.evaluate(vars);
                        Object key = argument.evaluate(vars);
                        return source instanceof Map<?, ?> map ? map.get(key) : null;
                    };
                }
                else if (matchSymbol("[")) {
                    Node key = parseConditional();
                    expectSymbol("]");
                    Node target = value;
                    value = vars -> {
                        Object source = target.evaluate(vars);
                        Object index = key.evaluate(vars);
                        if (source instanceof Map<?, ?> map) return map.get(index);
                        if (source instanceof List<?> list && index instanceof Number number) return list.get(number.intValue());
                        return null;
                    };
                }
                else break;
            }
            return value;
        }

        private Node parsePrimary() {
            Token token = current();
            if (match(TokenType.STRING)) return vars -> token.stringValue();
            if (match(TokenType.NUMBER)) return vars -> token.number();
            if (match(TokenType.WORD)) {
                return switch (token.text()) {
                    case "None", "null" -> vars -> null;
                    case "True", "true" -> vars -> true;
                    case "False", "false" -> vars -> false;
                    default -> vars -> {
                        if (!vars.containsKey(token.text())) throw invalid("变量未定义: " + token.text());
                        return vars.get(token.text());
                    };
                };
            }
            if (matchSymbol("(")) {
                Node value = parseConditional();
                expectSymbol(")");
                return value;
            }
            if (matchSymbol("[")) {
                List<Node> values = new ArrayList<>();
                if (!peekSymbol("]")) {
                    do values.add(parseConditional()); while (matchSymbol(",") && !peekSymbol("]"));
                }
                expectSymbol("]");
                return vars -> values.stream().map(item -> item.evaluate(vars)).toList();
            }
            if (matchSymbol("{")) {
                List<MapEntry> entries = new ArrayList<>();
                if (!peekSymbol("}")) {
                    do {
                        Token key = current();
                        if (!match(TokenType.STRING) && !match(TokenType.WORD)) throw invalid("对象键必须是字符串");
                        expectSymbol(":");
                        entries.add(new MapEntry(key.textValue(), parseConditional()));
                    } while (matchSymbol(",") && !peekSymbol("}"));
                }
                expectSymbol("}");
                return vars -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    entries.forEach(entry -> value.put(entry.key(), entry.value().evaluate(vars)));
                    return value;
                };
            }
            throw invalid("无法解析表达式附近内容: " + token.text());
        }

        private Node binary(Node left, Node right, BinaryOperation operation) {
            return vars -> operation.apply(left.evaluate(vars), right.evaluate(vars));
        }

        private boolean match(TokenType type) {
            if (current().type() != type) return false;
            position++;
            return true;
        }

        private boolean matchSymbol(String symbol) {
            if (!peekSymbol(symbol)) return false;
            position++;
            return true;
        }

        private boolean matchWord(String word) {
            if (current().type() != TokenType.WORD || !word.equals(current().text())) return false;
            position++;
            return true;
        }

        private boolean peekSymbol(String symbol) {
            return current().type() == TokenType.SYMBOL && symbol.equals(current().text());
        }

        private void expectSymbol(String symbol) {
            if (!matchSymbol(symbol)) throw invalid("期望符号: " + symbol);
        }

        private void expectWord(String word) {
            if (!matchWord(word)) throw invalid("期望关键字: " + word);
        }

        private Token expect(TokenType type, String message) {
            Token token = current();
            if (token.type() != type) throw invalid(message + ": " + token.text());
            position++;
            return token;
        }

        private Token current() {
            return tokens.get(Math.min(position, tokens.size() - 1));
        }

        private interface BinaryOperation {
            Object apply(Object left, Object right);
        }

        private record MapEntry(String key, Node value) {
        }
    }

    private enum TokenType { WORD, STRING, NUMBER, SYMBOL, END }

    private record Token(TokenType type, String text, Object value) {
        String stringValue() { return String.valueOf(value); }
        String textValue() { return type == TokenType.STRING ? stringValue() : text; }
        Number number() { return (Number) value; }
    }

    private static final class Lexer {
        private final String source;
        private int position;

        private Lexer(String source) { this.source = source; }

        List<Token> tokens() {
            List<Token> tokens = new ArrayList<>();
            while (position < source.length()) {
                char ch = source.charAt(position);
                if (Character.isWhitespace(ch)) { position++; continue; }
                if (ch == '\'' || ch == '"') { tokens.add(string(ch)); continue; }
                if (Character.isDigit(ch) || ch == '.' && position + 1 < source.length() && Character.isDigit(source.charAt(position + 1))) {
                    tokens.add(number()); continue;
                }
                if (Character.isLetter(ch) || ch == '_') {
                    int start = position++;
                    while (position < source.length() && (Character.isLetterOrDigit(source.charAt(position)) || source.charAt(position) == '_')) position++;
                    tokens.add(new Token(TokenType.WORD, source.substring(start, position), null));
                    continue;
                }
                String two = position + 1 < source.length() ? source.substring(position, position + 2) : "";
                if (List.of("==", "!=", ">=", "<=").contains(two)) {
                    tokens.add(new Token(TokenType.SYMBOL, two, null)); position += 2; continue;
                }
                if ("[]{}(),:.+-*/%<>".indexOf(ch) >= 0) {
                    tokens.add(new Token(TokenType.SYMBOL, String.valueOf(ch), null)); position++; continue;
                }
                throw invalid("不支持的字符: " + ch);
            }
            tokens.add(new Token(TokenType.END, "<end>", null));
            return tokens;
        }

        private Token string(char quote) {
            position++;
            StringBuilder value = new StringBuilder();
            while (position < source.length()) {
                char ch = source.charAt(position++);
                if (ch == quote) return new Token(TokenType.STRING, value.toString(), value.toString());
                if (ch == '\\' && position < source.length()) {
                    char escaped = source.charAt(position++);
                    value.append(switch (escaped) {
                        case 'n' -> '\n'; case 'r' -> '\r'; case 't' -> '\t'; default -> escaped;
                    });
                }
                else value.append(ch);
            }
            throw invalid("字符串未闭合");
        }

        private Token number() {
            int start = position;
            while (position < source.length() && (Character.isDigit(source.charAt(position)) || ".eE+-".indexOf(source.charAt(position)) >= 0)) {
                if ((source.charAt(position) == '+' || source.charAt(position) == '-') && position > start && "eE".indexOf(source.charAt(position - 1)) < 0) break;
                position++;
            }
            String text = source.substring(start, position);
            try {
                return new Token(TokenType.NUMBER, text, text.contains(".") || text.contains("e") || text.contains("E") ? Double.valueOf(text) : Long.valueOf(text));
            }
            catch (NumberFormatException error) {
                throw invalid("数字不合法: " + text);
            }
        }
    }

    private static boolean contains(Object value, Object container) {
        if (container instanceof Map<?, ?> map) return map.containsKey(value);
        return container instanceof Collection<?> collection && collection.stream().anyMatch(item -> equalsValue(value, item));
    }

    private static boolean equalsValue(Object left, Object right) {
        if (left instanceof Number a && right instanceof Number b) return new BigDecimal(a.toString()).compareTo(new BigDecimal(b.toString())) == 0;
        return Objects.equals(left, right);
    }

    private static int compare(Object left, Object right) {
        if (left instanceof Number && right instanceof Number) return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()));
        return String.valueOf(left).compareTo(String.valueOf(right));
    }

    private static Number number(Object value) {
        if (value instanceof Number item) return item;
        throw invalid("只能对数字进行运算: " + value);
    }

    private static Object add(Object left, Object right) {
        if (left instanceof String || right instanceof String) return String.valueOf(left) + right;
        return number(left).doubleValue() + number(right).doubleValue();
    }

    private static Object subtract(Object left, Object right) {
        return number(left).doubleValue() - number(right).doubleValue();
    }
}

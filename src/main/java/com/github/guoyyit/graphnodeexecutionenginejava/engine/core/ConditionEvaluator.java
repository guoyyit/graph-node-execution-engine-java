package com.github.guoyyit.graphnodeexecutionenginejava.engine.core;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 连线条件求值器。基于上游节点的输出求值。
 *
 * 支持的表达式（可用 && 与 || 组合，&& 优先级更高，不支持括号）：
 * <ul>
 *   <li>{@code key} —— 值真值判断（非 null、非 false、非空串）</li>
 *   <li>{@code key == literal} / {@code key != literal}</li>
 *   <li>{@code key > n} / {@code >=} / {@code <} / {@code <=}（数值比较）</li>
 * </ul>
 * literal 支持 'string'、"string"、数字、true/false。
 */
public final class ConditionEvaluator {

    private static final Pattern COMPARISON =
            Pattern.compile("^([A-Za-z_][A-Za-z0-9_.]*)\\s*(==|!=|>=|<=|>|<)\\s*(.+)$");
    private static final Pattern KEY =
            Pattern.compile("^([A-Za-z_][A-Za-z0-9_.]*)$");

    private ConditionEvaluator() {
    }

    public static boolean evaluate(String expression, Map<String, Object> upstreamOutput) {
        if (expression == null || expression.isBlank()) {
            return true;
        }
        for (String orPart : expression.split("\\|\\|")) {
            boolean andResult = true;
            for (String andPart : orPart.split("&&")) {
                andResult &= evalAtom(andPart.trim(), upstreamOutput);
            }
            if (andResult) {
                return true;
            }
        }
        return false;
    }

    private static boolean evalAtom(String atom, Map<String, Object> output) {
        Matcher cmp = COMPARISON.matcher(atom);
        if (cmp.matches()) {
            Object left = resolve(cmp.group(1), output);
            Object right = parseLiteral(cmp.group(3).trim());
            return compare(left, cmp.group(2), right);
        }
        Matcher key = KEY.matcher(atom);
        if (key.matches()) {
            return truthy(resolve(key.group(1), output));
        }
        throw new IllegalArgumentException("unsupported condition expression: " + atom);
    }

    private static Object resolve(String path, Map<String, Object> output) {
        Object current = output;
        for (String segment : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
        }
        return current;
    }

    private static Object parseLiteral(String token) {
        if ((token.startsWith("'") && token.endsWith("'"))
                || (token.startsWith("\"") && token.endsWith("\""))) {
            return token.substring(1, token.length() - 1);
        }
        if ("true".equalsIgnoreCase(token)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(token)) {
            return Boolean.FALSE;
        }
        if ("null".equalsIgnoreCase(token)) {
            return null;
        }
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException ignored) {
            try {
                return Double.parseDouble(token);
            } catch (NumberFormatException ignored2) {
                return token;
            }
        }
    }

    private static boolean compare(Object left, String op, Object right) {
        if ("==".equals(op)) {
            return left == null ? right == null : left.equals(right) || numericEq(left, right);
        }
        if ("!=".equals(op)) {
            return !compare(left, "==", right);
        }
        double l = toDouble(left, op);
        double r = toDouble(right, op);
        return switch (op) {
            case ">" -> l > r;
            case ">=" -> l >= r;
            case "<" -> l < r;
            case "<=" -> l <= r;
            default -> throw new IllegalArgumentException("unsupported operator: " + op);
        };
    }

    private static boolean numericEq(Object left, Object right) {
        return left instanceof Number && right instanceof Number
                && Double.compare(((Number) left).doubleValue(), ((Number) right).doubleValue()) == 0;
    }

    private static double toDouble(Object value, String op) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        throw new IllegalArgumentException(
                "operator " + op + " requires numeric operands, got: " + value);
    }

    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof String s) {
            return !s.isEmpty();
        }
        if (value instanceof Number n) {
            return n.doubleValue() != 0;
        }
        return true;
    }
}

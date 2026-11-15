package com.easywing.platform.ai.tool.impl;

import com.easywing.platform.ai.tool.AiTool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 计算器工具 - 执行数学表达式计算
 */
@Slf4j
@Component
public class CalculatorTool implements AiTool {

    public CalculatorTool() {
    }

    @Override
    public String getName() {
        return "calculator";
    }

    @Override
    public String getDescription() {
        return "执行数学计算。支持基本运算（+、-、*、/）、幂运算（**）、括号等。" +
                "输入参数：expression (string) - 数学表达式，例如: '2 + 2', '(10 * 5) / 2', 'Math.sqrt(16)'";
    }

    @Override
    public String getParametersSchema() {
        return """
                {
                  "type": "object",
                  "properties": {
                    "expression": {
                      "type": "string",
                      "description": "数学表达式",
                      "minLength": 1,
                      "maxLength": 500
                    }
                  },
                  "required": ["expression"]
                }
                """;
    }

    @Override
    public Mono<Map<String, Object>> execute(Map<String, Object> arguments) {
        return Mono.fromCallable(() -> {
            Object rawExpression = arguments.get("expression");
            String expression = rawExpression == null ? null : rawExpression.toString();
            if (expression == null || expression.trim().isEmpty()) {
                throw new IllegalArgumentException("expression 参数不能为空");
            }

            log.info("Executing calculation: {}", expression);

            // 安全检查：只允许数学运算
            if (!isSafeExpression(expression)) {
                throw new SecurityException("表达式包含不安全的内容");
            }

            try {
                double result = new ExpressionEvaluator(expression).evaluate();
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("expression", expression);
                response.put("result", result);
                response.put("resultType", Double.class.getSimpleName());

                log.info("Calculation result: {} = {}", expression, result);
                return response;
            } catch (Exception e) {
                log.error("Calculation failed: {}", expression, e);
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("expression", expression);
                response.put("error", e.getMessage());
                return response;
            }
        });
    }

    private boolean isSafeExpression(String expression) {
        return expression.matches("[0-9a-zA-Z.\\s+\\-*/%(),]+");
    }

    /**
     * 轻量算术表达式求值器，替代 JDK 15 起被移除的 Nashorn ScriptEngine。
     * 支持 +、-、*、/、%、**、括号、一元正负号以及常用数学函数。
     */
    static final class ExpressionEvaluator {

        private final String text;
        private int pos;

        ExpressionEvaluator(String text) {
            this.text = text;
        }

        double evaluate() {
            double value = parseExpression();
            skipWhitespace();
            if (pos < text.length()) {
                throw new IllegalArgumentException("无法解析的字符: " + text.charAt(pos));
            }
            return value;
        }

        private double parseExpression() {
            double value = parseTerm();
            while (true) {
                skipWhitespace();
                if (match('+')) {
                    value += parseTerm();
                } else if (match('-')) {
                    value -= parseTerm();
                } else {
                    return value;
                }
            }
        }

        private double parseTerm() {
            double value = parsePower();
            while (true) {
                skipWhitespace();
                if (match('*')) {
                    value *= parsePower();
                } else if (match('/')) {
                    value /= parsePower();
                } else if (match('%')) {
                    value %= parsePower();
                } else {
                    return value;
                }
            }
        }

        private double parsePower() {
            double base = parseUnary();
            skipWhitespace();
            if (peek() == '*' && pos + 1 < text.length() && text.charAt(pos + 1) == '*') {
                pos += 2;
                return Math.pow(base, parsePower());
            }
            return base;
        }

        private double parseUnary() {
            skipWhitespace();
            if (match('+')) {
                return parseUnary();
            }
            if (match('-')) {
                return -parseUnary();
            }
            return parsePrimary();
        }

        private double parsePrimary() {
            skipWhitespace();
            if (match('(')) {
                double value = parseExpression();
                skipWhitespace();
                if (!match(')')) {
                    throw new IllegalArgumentException("缺少右括号");
                }
                return value;
            }
            char current = peek();
            if (Character.isDigit(current) || current == '.') {
                return parseNumber();
            }
            if (Character.isLetter(current)) {
                return parseFunction();
            }
            throw new IllegalArgumentException("意外的字符: " + current);
        }

        private double parseNumber() {
            int start = pos;
            boolean dotSeen = false;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' && !dotSeen) {
                    dotSeen = true;
                    pos++;
                } else {
                    break;
                }
            }
            return Double.parseDouble(text.substring(start, pos));
        }

        private double parseFunction() {
            String name = parseIdentifier();
            skipWhitespace();
            if (peek() == '.') {
                pos++;
                name = parseIdentifier();
            }
            skipWhitespace();
            if (!match('(')) {
                throw new IllegalArgumentException("函数 " + name + " 缺少括号");
            }
            List<Double> args = new ArrayList<>();
            skipWhitespace();
            if (!match(')')) {
                while (true) {
                    args.add(parseExpression());
                    skipWhitespace();
                    if (match(',')) {
                        continue;
                    }
                    if (match(')')) {
                        break;
                    }
                    throw new IllegalArgumentException("函数 " + name + " 参数解析失败");
                }
            }
            return applyFunction(name.toLowerCase(), args);
        }

        private String parseIdentifier() {
            int start = pos;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (Character.isLetterOrDigit(c) || c == '_') {
                    pos++;
                } else {
                    break;
                }
            }
            if (start == pos) {
                throw new IllegalArgumentException("函数名解析失败");
            }
            return text.substring(start, pos);
        }

        private double applyFunction(String name, List<Double> args) {
            switch (name) {
                case "sqrt":
                    requireArgs(name, args, 1);
                    return Math.sqrt(args.get(0));
                case "abs":
                    requireArgs(name, args, 1);
                    return Math.abs(args.get(0));
                case "floor":
                    requireArgs(name, args, 1);
                    return Math.floor(args.get(0));
                case "ceil":
                    requireArgs(name, args, 1);
                    return Math.ceil(args.get(0));
                case "round":
                    requireArgs(name, args, 1);
                    return Math.round(args.get(0));
                case "exp":
                    requireArgs(name, args, 1);
                    return Math.exp(args.get(0));
                case "log":
                    requireArgs(name, args, 1);
                    return Math.log(args.get(0));
                case "log10":
                    requireArgs(name, args, 1);
                    return Math.log10(args.get(0));
                case "pow":
                    requireArgs(name, args, 2);
                    return Math.pow(args.get(0), args.get(1));
                case "min":
                    return args.stream().mapToDouble(Double::doubleValue).min().orElseThrow();
                case "max":
                    return args.stream().mapToDouble(Double::doubleValue).max().orElseThrow();
                default:
                    throw new IllegalArgumentException("不支持的函数: " + name);
            }
        }

        private void requireArgs(String name, List<Double> args, int expected) {
            if (args.size() != expected) {
                throw new IllegalArgumentException("函数 " + name + " 需要 " + expected + " 个参数");
            }
        }

        private void skipWhitespace() {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
                pos++;
            }
        }

        private boolean match(char expected) {
            if (pos < text.length() && text.charAt(pos) == expected) {
                pos++;
                return true;
            }
            return false;
        }

        private char peek() {
            return pos < text.length() ? text.charAt(pos) : '\0';
        }
    }
}

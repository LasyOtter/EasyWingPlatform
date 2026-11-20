package com.easywing.platform.ai.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ReAct 响应解析器
 * <p>
 * 从模型输出中抽取 {@code 思考} / {@code 行动} / {@code 行动输入} / {@code 最终答案}。
 * 支持中文与英文标签，并容忍 Markdown 加粗与代码块包裹。
 * <p>
 * 解析失败时抛出 {@link ParseException}，由调用方决定重试或报错；
 * 不把"解析失败"伪装成"最终答案"返回给用户。
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
final class ReactParser {

    /**
     * 标签前的 Markdown 加粗 / 列表符号，例如 {@code **思考：**} 或 {@code - 行动：}。
     * 放在捕获组外，避免前缀混入捕获内容。
     */
    private static final String PREFIX = "(?:\\*\\*)?(?:[-*]\\s+)?";

    /**
     * 标签后的 Markdown 加粗闭合，例如 {@code 思考：**内容**}。
     */
    private static final String SUFFIX = "(?:\\*\\*)?";

    private static final Pattern THOUGHT = Pattern.compile(
            PREFIX + "(?:思考|Thought)[:：]" + SUFFIX + "\\s*(.+?)"
                    + "(?=" + PREFIX + "(?:行动|Action|最终答案|Observation|Final Answer)[:：]|$)",
            Pattern.DOTALL);

    private static final Pattern ACTION = Pattern.compile(
            PREFIX + "(?:行动|Action)[:：]" + SUFFIX + "\\s*([^\\n]+)");

    private static final Pattern ACTION_INPUT = Pattern.compile(
            PREFIX + "(?:行动输入|Action Input)[:：]" + SUFFIX + "\\s*(.+?)"
                    + "(?=" + PREFIX + "(?:观察|Observation|思考|Thought|最终答案|Final Answer)[:：]|$)",
            Pattern.DOTALL);

    private static final Pattern FINAL_ANSWER = Pattern.compile(
            PREFIX + "(?:最终答案|Final Answer)[:：]" + SUFFIX + "\\s*(.+)", Pattern.DOTALL);

    private static final Pattern CODE_FENCE = Pattern.compile(
            "^\\s*```[a-zA-Z]*\\s*\\n?(.*)\\n?```\\s*$", Pattern.DOTALL);

    private final ObjectMapper objectMapper;

    ReactParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 解析单轮输出。
     *
     * @throws ParseException 既无最终答案、也无法解析出行动时抛出
     */
    Parsed parse(String llmResponse) throws ParseException {
        if (llmResponse == null || llmResponse.isBlank()) {
            throw new ParseException("模型返回空内容");
        }
        String text = stripCodeFence(llmResponse);

        Matcher finalAnswer = FINAL_ANSWER.matcher(text);
        if (finalAnswer.find()) {
            return Parsed.finalAnswer(finalAnswer.group(1).trim());
        }

        Matcher action = ACTION.matcher(text);
        if (!action.find()) {
            throw new ParseException("响应中未找到行动指令，且无最终答案");
        }

        String actionName = action.group(1).trim();
        actionName = actionName.replace("**", "").replaceAll("^`|`$", "").trim();

        Map<String, Object> actionInput = parseActionInput(text);

        String thought = null;
        Matcher thoughtMatcher = THOUGHT.matcher(text);
        if (thoughtMatcher.find()) {
            thought = thoughtMatcher.group(1).trim();
        }

        return Parsed.action(thought, actionName, actionInput);
    }

    /**
     * 解析行动输入 JSON。失败时降级为 {@code {"input": 原文}}，
     * 由工具侧按缺参数处理，而非静默丢弃。
     */
    private Map<String, Object> parseActionInput(String text) {
        Matcher m = ACTION_INPUT.matcher(text);
        if (!m.find()) {
            return new HashMap<>();
        }
        String raw = stripCodeFence(m.group(1).trim());
        try {
            return objectMapper.readValue(raw, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("input", raw);
            return fallback;
        }
    }

    /**
     * 去掉整体被 ``` 包裹的情况。
     */
    private String stripCodeFence(String text) {
        Matcher m = CODE_FENCE.matcher(text.trim());
        return m.matches() ? m.group(1).trim() : text.trim();
    }

    /**
     * 解析结果
     */
    record Parsed(String thought, String action, Map<String, Object> actionInput,
                  String finalAnswer, boolean finalAnswerFound) {

        static Parsed action(String thought, String action, Map<String, Object> actionInput) {
            return new Parsed(thought, action, actionInput, null, false);
        }

        static Parsed finalAnswer(String answer) {
            return new Parsed(null, null, Map.of(), answer, true);
        }
    }

    /**
     * 解析失败
     */
    static class ParseException extends Exception {
        ParseException(String message) {
            super(message);
        }
    }
}

package com.easywing.platform.ai.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ReactParser} 测试
 * <p>
 * 覆盖中文 / 英文标签、Markdown 加粗、代码块包裹、缺行动报错等路径。
 */
class ReactParserTest {

    private final ReactParser parser = new ReactParser(new ObjectMapper());

    @Test
    @DisplayName("解析完整 ReAct 分段")
    void parsesThoughtActionAndInput() throws Exception {
        String response =
                """
                思考: 需要计算 2+2
                行动: calculator
                行动输入: {"expression": "2+2"}
                观察: 等待结果
                """;

        ReactParser.Parsed p = parser.parse(response);

        assertTrue(!p.finalAnswerFound());
        assertEquals("需要计算 2+2", p.thought());
        assertEquals("calculator", p.action());
        assertEquals("2+2", p.actionInput().get("expression"));
    }

    @Test
    @DisplayName("解析最终答案")
    void parsesFinalAnswer() throws Exception {
        String response = """
                思考: 已知结果
                最终答案: 4
                """;

        ReactParser.Parsed p = parser.parse(response);

        assertTrue(p.finalAnswerFound());
        assertEquals("4", p.finalAnswer());
    }

    @Test
    @DisplayName("支持英文标签")
    void parsesEnglishLabels() throws Exception {
        String response =
                """
                Thought: need the time
                Action: current_time
                Action Input: {"timezone": "Asia/Shanghai"}
                """;

        ReactParser.Parsed p = parser.parse(response);

        assertEquals("current_time", p.action());
        assertEquals("Asia/Shanghai", p.actionInput().get("timezone"));
        assertEquals("need the time", p.thought());
    }

    @Test
    @DisplayName("容忍 Markdown 加粗标签")
    void parsesBoldLabels() throws Exception {
        String response = "**思考：** 查时间\n**行动：** current_time\n**行动输入：** {}";

        ReactParser.Parsed p = parser.parse(response);

        assertEquals("current_time", p.action());
        assertEquals("查时间", p.thought());
    }

    @Test
    @DisplayName("行动输入为代码块时正确提取 JSON")
    void parsesFencedJsonInput() throws Exception {
        String response =
                """
                思考: 计算
                行动: calculator
                行动输入:
                ```json
                {"expression": "3*3"}
                ```
                观察: x
                """;

        ReactParser.Parsed p = parser.parse(response);

        assertEquals("calculator", p.action());
        assertEquals("3*3", p.actionInput().get("expression"));
    }

    @Test
    @DisplayName("行动名带 Markdown 强调时清洗")
    void stripsMarkdownFromActionName() throws Exception {
        String response = "思考: x\n行动: **calculator**\n行动输入: {\"expression\": \"1\"}";

        assertEquals("calculator", parser.parse(response).action());
    }

    @Test
    @DisplayName("行动输入非 JSON 时降级为 input 字段")
    void fallsBackToTextInput() throws Exception {
        String response = "思考: x\n行动: echo\n行动输入: 纯文本";

        ReactParser.Parsed p = parser.parse(response);

        assertEquals("纯文本", p.actionInput().get("input"));
    }

    @Test
    @DisplayName("无行动也无最终答案时抛 ParseException")
    void rejectsAmbiguousResponse() {
        assertThrows(ReactParser.ParseException.class, () -> parser.parse("这是一个没有结构的回答"));
    }

    @Test
    @DisplayName("空响应抛 ParseException")
    void rejectsEmptyResponse() {
        assertThrows(ReactParser.ParseException.class, () -> parser.parse("   "));
    }

    @Test
    @DisplayName("缺少行动输入时返回空 Map")
    void missingActionInputYieldsEmptyMap() throws Exception {
        ReactParser.Parsed p = parser.parse("思考: x\n行动: calculator");

        assertEquals(Map.of(), p.actionInput());
    }

    @Test
    @DisplayName("整段被代码块包裹时仍能解析")
    void parsesFullyFencedResponse() throws Exception {
        String response =
                """
                ```
                思考: 计算
                行动: calculator
                行动输入: {"expression": "1+1"}
                ```
                """;

        ReactParser.Parsed p = parser.parse(response);

        assertEquals("calculator", p.action());
        assertNull(p.finalAnswer());
    }
}

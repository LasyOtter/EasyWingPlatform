package com.easywing.platform.ai.artifact;

import static org.assertj.core.api.Assertions.assertThat;

import com.easywing.platform.ai.artifact.ArtifactExtractor.Extracted;
import java.util.List;
import org.junit.jupiter.api.Test;

class ArtifactExtractorTest {

    @Test
    void extractsSupportedFencedBlocksInOrder() {
        String content = "分析结果：\n"
                + "```html\n<div class=\"a\">hi</div>\n```\n"
                + "再给一个图：\n"
                + "```mermaid\ngraph TD; A-->B\n```\n"
                + "```react\nfunction App(){return <div/>;}\n```";

        List<Extracted> extracted = ArtifactExtractor.extract(content);

        assertThat(extracted).hasSize(3);
        assertThat(extracted.get(0).type()).isEqualTo(Artifact.Type.HTML);
        assertThat(extracted.get(0).code()).isEqualTo("<div class=\"a\">hi</div>");
        assertThat(extracted.get(1).type()).isEqualTo(Artifact.Type.MERMAID);
        assertThat(extracted.get(2).type()).isEqualTo(Artifact.Type.REACT);
        assertThat(extracted.get(2).language()).isEqualTo("react");
    }

    @Test
    void ignoresUnsupportedLanguagesAndTooShortBlocks() {
        String content = "```python\nprint(1)\n```\n```html\nx\n```\n```\n\n```";

        assertThat(ArtifactExtractor.extract(content)).isEmpty();
    }

    @Test
    void handlesNullAndBlankContent() {
        assertThat(ArtifactExtractor.extract(null)).isEmpty();
        assertThat(ArtifactExtractor.extract("")).isEmpty();
        assertThat(ArtifactExtractor.extract("   ")).isEmpty();
    }

    @Test
    void usesFirstLineAsTitleAndTruncatesLongLines() {
        Extracted shortTitle =
                ArtifactExtractor.extract("```html\n<div>hi</div>\n```").get(0);
        assertThat(shortTitle.title()).isEqualTo("<div>hi</div>");

        String longLine = "a".repeat(70);
        Extracted longTitle =
                ArtifactExtractor.extract("```html\n" + longLine + "\n```").get(0);
        assertThat(longTitle.title()).hasSize(61).endsWith("…");
    }
}

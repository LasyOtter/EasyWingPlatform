package com.easywing.platform.ai.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link HtmlContentFetcher} 纯函数与 SSRF 判定测试（不触网）。
 */
class HtmlContentFetcherTest {

    private HtmlContentFetcher fetcher;

    @BeforeEach
    void setUp() {
        fetcher = new HtmlContentFetcher(null, 1024, 20000);
    }

    @Test
    @DisplayName("去除脚本样式与标签并解码实体")
    void stripsMarkup() {
        String html = "<html><head><style>body{color:red}</style></head><body>"
                + "<p>Hello&nbsp;World</p><script>alert(1)</script><div>a &amp; b</div></body></html>";

        String text = HtmlContentFetcher.htmlToText(html);

        assertThat(text).contains("Hello World").contains("a & b");
        assertThat(text).doesNotContain("color:red").doesNotContain("alert(1)").doesNotContain("<p>");
    }

    @Test
    @DisplayName("空与空白的 HTML 返回空串")
    void blankHtml() {
        assertThat(HtmlContentFetcher.htmlToText(null)).isEmpty();
        assertThat(HtmlContentFetcher.htmlToText("   ")).isEmpty();
    }

    @Test
    @DisplayName("拒绝内网、环回与保留地址")
    void rejectsNonPublicHosts() {
        assertThat(fetcher.isPublicHost("localhost")).isFalse();
        assertThat(fetcher.isPublicHost("127.0.0.1")).isFalse();
        assertThat(fetcher.isPublicHost("10.1.2.3")).isFalse();
        assertThat(fetcher.isPublicHost("192.168.1.1")).isFalse();
        assertThat(fetcher.isPublicHost("172.16.0.1")).isFalse();
        assertThat(fetcher.isPublicHost("169.254.169.254")).isFalse();
        assertThat(fetcher.isPublicHost("0.0.0.0")).isFalse();
        assertThat(fetcher.isPublicHost("::1")).isFalse();
        assertThat(fetcher.isPublicHost("fd00::1")).isFalse();
        assertThat(fetcher.isPublicHost("not-a-host")).isFalse();
    }

    @Test
    @DisplayName("放行公网地址")
    void allowsPublicHosts() {
        assertThat(fetcher.isPublicHost("8.8.8.8")).isTrue();
        assertThat(fetcher.isPublicHost("93.184.216.34")).isTrue();
    }

    @Test
    @DisplayName("按地址类型判定保留地址")
    void blocksReservedAddresses() throws Exception {
        assertThat(fetcher.isBlockedAddress(InetAddress.getByName("127.0.0.1"))).isTrue();
        assertThat(fetcher.isBlockedAddress(InetAddress.getByName("10.0.0.5"))).isTrue();
        assertThat(fetcher.isBlockedAddress(InetAddress.getByName("8.8.8.8"))).isFalse();
    }
}

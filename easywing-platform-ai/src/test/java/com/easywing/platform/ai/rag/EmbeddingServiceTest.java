package com.easywing.platform.ai.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.easywing.platform.ai.config.AiProperties;
import java.lang.reflect.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * {@link EmbeddingService} 测试
 * <p>
 * 覆盖本地回退向量与远程响应解析。远程路径用夹具验证解析逻辑，不发起真实请求。
 */
class EmbeddingServiceTest {

    private EmbeddingService fallbackService;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties();
        // 不配置 openai apiKey -> 走本地确定性回退
        fallbackService = new EmbeddingService(WebClient.builder(), properties);
    }

    @Test
    @DisplayName("本地回退：同一文本向量稳定，不同文本向量不同")
    void fallbackIsDeterministic() {
        float[] a = fallbackService.embed("hello world").block();
        float[] b = fallbackService.embed("hello world").block();
        float[] c = fallbackService.embed("goodbye").block();

        assertEquals(a.length, b.length);
        for (int i = 0; i < a.length; i++) {
            assertEquals(a[i], b[i], 0.0f, "同一文本应产生相同向量");
        }
        assertNotEquals(a[0], c[0], 0.0f);
    }

    @Test
    @DisplayName("本地回退：向量已归一化")
    void fallbackVectorIsNormalized() {
        float[] v = fallbackService.embed("normalize me").block();

        double norm = 0.0;
        for (float x : v) {
            norm += x * x;
        }
        assertEquals(1.0, Math.sqrt(norm), 1e-5);
    }

    @Test
    @DisplayName("null 文本抛异常")
    void nullTextRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () -> fallbackService.embed(null).block());
    }

    @Test
    @DisplayName("空文本返回零向量")
    void emptyTextReturnsZeroVector() {
        float[] v = fallbackService.embed("").block();

        assertEquals(1536, v.length);
        for (float x : v) {
            assertEquals(0.0f, x);
        }
    }

    @Test
    @DisplayName("批量 embedding 保持顺序与维度")
    void batchPreservesOrder() {
        float[][] batch =
                fallbackService.embedBatch(java.util.List.of("a", "b", "c")).block();

        assertEquals(3, batch.length);
        assertEquals(batch[0].length, batch[1].length);
        assertEquals(batch[1].length, batch[2].length);
    }

    @Test
    @DisplayName("命中缓存：重复文本返回缓存向量")
    void cachesRepeatedText() {
        fallbackService.embed("cache me").block();
        fallbackService.embed("cache me").block();

        assertEquals(1, fallbackService.cachedTexts().size());
    }

    @Test
    @DisplayName("解析 OpenAI embeddings 响应并归一化")
    void parsesRemoteEmbedding() throws Exception {
        String json = """
                {"data":[{"embedding":[3.0,4.0]}]}
                """;

        Method m = EmbeddingService.class.getDeclaredMethod("parseEmbedding", String.class);
        m.setAccessible(true);
        float[] v = (float[]) m.invoke(fallbackService, json);

        // [3,4] 归一化后为 [0.6,0.8]
        assertEquals(2, v.length);
        assertEquals(0.6f, v[0], 1e-6);
        assertEquals(0.8f, v[1], 1e-6);
    }

    @Test
    @DisplayName("缺少 data 数组时抛异常")
    void rejectsMalformedEmbeddingResponse() throws Exception {
        Method m = EmbeddingService.class.getDeclaredMethod("parseEmbedding", String.class);
        m.setAccessible(true);

        Exception e = assertThrows(Exception.class, () -> m.invoke(fallbackService, "{\"data\":[]}"));
        assertTrue(e.getCause() != null || e instanceof IllegalStateException);
    }

    @Test
    @DisplayName("配置 apiKey 时走远程路径（不可达地址，无外部网络依赖）")
    void usesRemoteWhenApiKeyPresent() {
        AiProperties properties = new AiProperties();
        AiProperties.ProviderConfig openai = new AiProperties.ProviderConfig();
        openai.setApiKey("test-key");
        // 指向不可达端口：调用必然失败，但不产生对外部服务的真实依赖
        openai.setBaseUrl("http://127.0.0.1:1");
        properties.getProviders().put("openai", openai);

        EmbeddingService service = new EmbeddingService(WebClient.builder(), properties);

        // 走远程分支：因连接被拒而失败，而非返回本地回退向量
        assertThrows(RuntimeException.class, () -> service.embed("hello").block());
    }

    @Test
    @DisplayName("默认 embedding 模型为 text-embedding-3-small")
    void defaultModelIsStable() throws Exception {
        Method m = EmbeddingService.class.getDeclaredMethod("embeddingModel");
        m.setAccessible(true);

        assertEquals("text-embedding-3-small", m.invoke(fallbackService));
    }
}

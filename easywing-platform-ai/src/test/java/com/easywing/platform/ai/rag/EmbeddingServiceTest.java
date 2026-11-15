package com.easywing.platform.ai.rag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.easywing.platform.ai.config.AiProperties;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.test.StepVerifier;

class EmbeddingServiceTest {

    @Test
    void embedFallsBackToMockWhenApiKeyMissing() {
        EmbeddingService service = new EmbeddingService(new AiProperties(), WebClient.builder());

        float[] vector = service.embed("hello").block();

        assertThat(vector).hasSize(1536);
        double norm = 0.0;
        for (float value : vector) {
            norm += value * value;
        }
        norm = Math.sqrt(norm);
        assertThat(norm).isCloseTo(1.0, within(1e-3));
    }

    @Test
    void embedCachesFallbackVector() {
        EmbeddingService service = new EmbeddingService(new AiProperties(), WebClient.builder());

        float[] first = service.embed("cached").block();
        float[] second = service.embed("cached").block();

        assertThat(first).isSameAs(second);
    }

    @Test
    void embedBatchFallsBackToMock() {
        EmbeddingService service = new EmbeddingService(new AiProperties(), WebClient.builder());

        float[][] vectors = service.embedBatch(List.of("a", "b", "c")).block();

        assertThat(vectors.length).isEqualTo(3);
        assertThat(vectors[0]).hasSize(1536);
    }

    @Test
    void embedCallsOpenAiCompatibleEndpoint() {
        AiProperties properties = new AiProperties();
        properties.getEmbedding().setApiKey("test-key");
        properties.getEmbedding().setBaseUrl("http://localhost:9999");
        properties.getEmbedding().setModel("text-embedding-3-small");

        AtomicReference<String> authorization = new AtomicReference<>();
        ExchangeFunction exchangeFunction = request -> {
            authorization.set(request.headers().getFirst(HttpHeaders.AUTHORIZATION));
            return reactor.core.publisher.Mono.just(
                    jsonResponse("{\"data\":[{\"index\":0,\"embedding\":[0.1,0.2,0.3]}]}"));
        };
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
        EmbeddingService service = new EmbeddingService(properties, builder);

        float[] vector = service.embed("remote").block();

        assertThat(vector).containsExactly(0.1f, 0.2f, 0.3f);
        assertThat(authorization).hasValue("Bearer test-key");
    }

    @Test
    void embedPropagatesRemoteFailure() {
        AiProperties properties = new AiProperties();
        properties.getEmbedding().setApiKey("test-key");
        properties.getEmbedding().setBaseUrl("http://localhost:9999");

        ExchangeFunction exchangeFunction =
                request -> reactor.core.publisher.Mono.just(ClientResponse.create(HttpStatus.UNAUTHORIZED)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                        .body(dataBuffer("{\"error\":\"invalid api key\"}"))
                        .build());
        WebClient.Builder builder = WebClient.builder().exchangeFunction(exchangeFunction);
        EmbeddingService service = new EmbeddingService(properties, builder);

        StepVerifier.create(service.embed("remote"))
                .expectError(WebClientResponseException.class)
                .verify();
    }

    private static ClientResponse jsonResponse(String json) {
        return ClientResponse.create(HttpStatus.OK)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(dataBuffer(json))
                .build();
    }

    private static reactor.core.publisher.Flux<DataBuffer> dataBuffer(String json) {
        DataBuffer buffer = new DefaultDataBufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return reactor.core.publisher.Flux.just(buffer);
    }
}

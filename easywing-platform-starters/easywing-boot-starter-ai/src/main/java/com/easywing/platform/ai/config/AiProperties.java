/*
 * Copyright 2024-2026 EasyWing Platform Team.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.easywing.platform.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "easywing.ai")
public class AiProperties {

    private boolean enabled = true;

    private ChatModel chatModel = new ChatModel();

    private EmbeddingModel embeddingModel = new EmbeddingModel();

    private Rag rag = new Rag();

    private Agent agent = new Agent();

    @Data
    public static class ChatModel {
        private String provider = "openai";

        private OpenAi openAi = new OpenAi();

        private DeepSeek deepSeek = new DeepSeek();

        private Qwen qwen = new Qwen();

        private Ollama ollama = new Ollama();

        @Data
        public static class OpenAi {
            private String apiKey;
            private String modelName = "gpt-4o-mini";
            private String baseUrl = "https://api.openai.com/v1";
            private Double temperature = 0.7;
            private Integer maxTokens = 4000;
            private Double topP = 1.0;
            private Duration timeout = Duration.ofSeconds(60);
            private Boolean logRequests = false;
            private Boolean logResponses = false;
        }

        @Data
        public static class DeepSeek {
            private String apiKey;
            private String modelName = "deepseek-chat";
            private String baseUrl = "https://api.deepseek.com/v1";
            private Double temperature = 0.7;
            private Integer maxTokens = 4000;
            private Duration timeout = Duration.ofSeconds(60);
        }

        @Data
        public static class Qwen {
            private String apiKey;
            private String modelName = "qwen-plus";
            private Double temperature = 0.7;
            private Integer maxTokens = 4000;
            private Boolean enableSearch = false;
        }

        @Data
        public static class Ollama {
            private String baseUrl = "http://localhost:11434";
            private String modelName = "llama3.2";
            private Double temperature = 0.7;
            private Integer numPredict = 2048;
        }
    }

    @Data
    public static class EmbeddingModel {
        private String provider = "openai";

        private OpenAiEmbedding openAi = new OpenAiEmbedding();

        private QwenEmbedding qwen = new QwenEmbedding();

        private LocalEmbedding local = new LocalEmbedding();

        @Data
        public static class OpenAiEmbedding {
            private String apiKey;
            private String modelName = "text-embedding-3-small";
            private String baseUrl = "https://api.openai.com/v1";
            private Integer dimensions = 1536;
        }

        @Data
        public static class QwenEmbedding {
            private String apiKey;
            private String modelName = "text-embedding-v3";
        }

        @Data
        public static class LocalEmbedding {
            private Boolean enabled = false;
            private String modelName = "all-MiniLM-L6-v2";
        }
    }

    @Data
    public static class Rag {
        private Boolean enabled = true;

        private DocumentLoader documentLoader = new DocumentLoader();

        private VectorStore vectorStore = new VectorStore();

        @Data
        public static class DocumentLoader {
            private Boolean enabled = true;
            private List<String> resourcePaths = List.of("classpath:/documents/");
            private Integer chunkSize = 1000;
            private Integer chunkOverlap = 200;
        }

        @Data
        public static class VectorStore {
            private String type = "in-memory";
            private String persistDirectory;
        }
    }

    @Data
    public static class Agent {
        private Boolean enabled = false;

        private Tools tools = new Tools();

        private Memory memory = new Memory();

        @Data
        public static class Tools {
            private Boolean enabled = true;
            private List<String> includePackages = List.of("com.easywing.platform.ai.tools");
        }

        @Data
        public static class Memory {
            private String type = "in-memory";
            private Integer maxMessages = 100;
        }
    }
}
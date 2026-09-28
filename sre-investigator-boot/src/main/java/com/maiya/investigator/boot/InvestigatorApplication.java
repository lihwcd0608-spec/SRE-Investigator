package com.maiya.investigator.boot;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAgentAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioSpeechAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeAudioTranscriptionAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeEmbeddingAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeImageAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeRerankAutoConfiguration;
import com.maiya.investigator.boot.auth.AuthProperties;
import com.maiya.investigator.boot.model.ModelProperties;
import com.maiya.investigator.graph.AgentProperties;
import com.maiya.investigator.graph.route.InvestigationRouteProperties;
import com.maiya.investigator.infra.memory.ControlPlaneRedisProperties;
import com.maiya.investigator.infra.memory.MemoryProperties;
import com.maiya.investigator.mcp.config.AliyunCloudProperties;
import com.maiya.investigator.mcp.config.ReadonlyToolProperties;
import com.maiya.investigator.mcp.config.RepositoryWorkspaceProperties;
import org.springframework.ai.model.anthropic.autoconfigure.AnthropicChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration;
import org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioSpeechAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAudioTranscriptionAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiImageAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.OpenAiModerationAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration;
import org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(
        scanBasePackages = "com.maiya.investigator",
        exclude = {
                DashScopeAgentAutoConfiguration.class,
                DashScopeChatAutoConfiguration.class,
                DashScopeEmbeddingAutoConfiguration.class,
                DashScopeImageAutoConfiguration.class,
                DashScopeRerankAutoConfiguration.class,
                DashScopeAudioSpeechAutoConfiguration.class,
                DashScopeAudioTranscriptionAutoConfiguration.class,
                OpenAiChatAutoConfiguration.class,
                OpenAiEmbeddingAutoConfiguration.class,
                OpenAiImageAutoConfiguration.class,
                OpenAiModerationAutoConfiguration.class,
                OpenAiAudioSpeechAutoConfiguration.class,
                OpenAiAudioTranscriptionAutoConfiguration.class,
                AnthropicChatAutoConfiguration.class,
                OllamaChatAutoConfiguration.class,
                OllamaEmbeddingAutoConfiguration.class,
                MongoAutoConfiguration.class,
                MongoDataAutoConfiguration.class,
                MongoRepositoriesAutoConfiguration.class
        }
)
@EnableScheduling
@EnableConfigurationProperties({
        AliyunCloudProperties.class,
        ReadonlyToolProperties.class,
        ModelProperties.class,
        RepositoryWorkspaceProperties.class,
        AuthProperties.class,
        InvestigationRouteProperties.class,
        ControlPlaneRedisProperties.class,
        MemoryProperties.class,
        AgentProperties.class
})
public class InvestigatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(InvestigatorApplication.class, args);
    }
}

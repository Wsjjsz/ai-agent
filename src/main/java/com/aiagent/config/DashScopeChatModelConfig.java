package com.aiagent.config;

import com.aiagent.llm.DashScopeCompatibleChatModel;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatProperties;
import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeConnectionProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.observation.ChatModelObservationConvention;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties({DashScopeConnectionProperties.class, DashScopeChatProperties.class})
@Slf4j
public class DashScopeChatModelConfig {

    @Value("${app.dashscope.compatible-base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String compatibleBaseUrl;

    @Bean(name = "dashscopeChatModel")
    public ChatModel dashscopeChatModel(
            RetryTemplate retryTemplate,
            ToolCallingManager toolCallingManager,
            DashScopeChatProperties chatProperties,
            ResponseErrorHandler responseErrorHandler,
            DashScopeConnectionProperties connectionProperties,
            ObjectProvider<ObservationRegistry> observationRegistry,
            ObjectProvider<WebClient.Builder> webClientBuilderProvider,
            ObjectProvider<RestClient.Builder> restClientBuilderProvider,
            ObjectProvider<ChatModelObservationConvention> observationConvention,
            ObjectProvider<ToolExecutionEligibilityPredicate> toolExecutionEligibilityPredicate,
            ObjectMapper objectMapper) {
        String model = chatProperties.getOptions() == null ? null : chatProperties.getOptions().getModel();
        String apiKey = firstNonBlank(chatProperties.getApiKey(), connectionProperties.getApiKey());
        if (requiresCompatibleMode(model)) {
            log.info("Using DashScope OpenAI-compatible chat endpoint for model {}", model);
            return new DashScopeCompatibleChatModel(apiKey, compatibleBaseUrl, model, objectMapper);
        }
        log.info("Using native Spring AI Alibaba DashScope chat endpoint for model {}", model);
        return new DashScopeChatAutoConfiguration().dashscopeChatModel(
                retryTemplate,
                toolCallingManager,
                chatProperties,
                responseErrorHandler,
                connectionProperties,
                observationRegistry,
                webClientBuilderProvider,
                restClientBuilderProvider,
                observationConvention,
                toolExecutionEligibilityPredicate
        );
    }

    private boolean requiresCompatibleMode(String model) {
        if (model == null) {
            return false;
        }
        String normalized = model.toLowerCase();
        return normalized.startsWith("qwen3.") || normalized.startsWith("deepseek-");
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

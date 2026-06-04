package com.aiagent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * Logs startup prerequisites in one place so local setup problems are easy to spot.
 */
@Component
@Slf4j
public class ApplicationStartupValidator implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final ResourcePatternResolver resourcePatternResolver;
    private final Environment environment;

    @Value("${server.port:8123}")
    private String serverPort;

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${spring.ai.dashscope.chat.options.model:}")
    private String dashscopeChatModel;

    @Value("${search-api.api-key:}")
    private String searchApiKey;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${app.auth.sms-provider:mock}")
    private String smsProvider;

    @Value("${app.auth.sms-http.secret:}")
    private String smsHttpSecret;

    @Value("${app.auth.jwt-secret:}")
    private String jwtSecret;

    @Value("${app.auth.sms-code-secret:}")
    private String smsCodeSecret;

    @Value("${app.api-key:}")
    private String appApiKey;

    public ApplicationStartupValidator(JdbcTemplate jdbcTemplate, ResourcePatternResolver resourcePatternResolver, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.resourcePatternResolver = resourcePatternResolver;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("AI Agent service endpoint: http://localhost:{}{}", serverPort, contextPath);
        validateDatabase();
        validateRagDocuments();
        validateConfiguredValue("DashScope API Key", dashscopeApiKey, "dashscope-local-dev-key");
        validateDashScopeChatModel();
        validateConfiguredValue("Search API Key", searchApiKey);
        validateProductionSafety();
    }

    private void validateDatabase() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (Integer.valueOf(1).equals(result)) {
                log.info("Startup check passed: database is reachable ({})", datasourceUrl);
                return;
            }
            log.warn("Startup check warning: database validation returned an unexpected result");
        } catch (Exception e) {
            log.warn("Startup check warning: database is not reachable ({}): {}", datasourceUrl, e.getMessage());
        }
    }

    private void validateRagDocuments() {
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            if (resources.length == 0) {
                log.warn("Startup check warning: no RAG documents found under classpath:document/*.md");
                return;
            }
            log.info("Startup check passed: {} RAG markdown documents found", resources.length);
        } catch (Exception e) {
            log.warn("Startup check warning: failed to scan RAG documents: {}", e.getMessage());
        }
    }

    private void validateConfiguredValue(String name, String value) {
        if (value == null || value.isBlank()) {
            log.warn("Startup check warning: {} is not configured", name);
            return;
        }
        log.info("Startup check passed: {} is configured", name);
    }

    private void validateDashScopeChatModel() {
        if (dashscopeChatModel == null || dashscopeChatModel.isBlank()) {
            log.warn("Startup check warning: DashScope chat model is not configured");
            return;
        }
        String normalized = dashscopeChatModel.toLowerCase();
        if (normalized.contains("-vl") || normalized.contains("qwen3.6")) {
            log.warn("Startup check warning: DashScope chat model '{}' may require multimodal-generation/withMultiModel. The current app uses the text ChatModel chain, so prefer qwen3-max, qwen-plus, or deepseek-v4-pro.", dashscopeChatModel);
            return;
        }
        log.info("Startup check passed: DashScope chat model is configured ({})", dashscopeChatModel);
    }

    private void validateProductionSafety() {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (!prod) {
            return;
        }

        requireStrongSecret("APP_API_KEY", appApiKey, 32, "change-me");
        requireStrongSecret("APP_AUTH_JWT_SECRET", jwtSecret, 32, "ai-agent-local-dev-secret-change-me", "change-me");
        requireStrongSecret("APP_AUTH_SMS_CODE_SECRET", smsCodeSecret, 32, "ai-agent-sms-local-secret-change-me", "change-me");
        requireStrongSecret("DB_PASSWORD", datasourcePassword, 16, "psswd", "password", "change-me");
        requireStrongSecret("DASHSCOPE_API_KEY", dashscopeApiKey, 16, "dashscope-local-dev-key", "change-me");

        if ("mock".equalsIgnoreCase(smsProvider)) {
            throw new IllegalStateException("Production profile cannot use mock SMS provider. Set app.auth.sms-provider=http and configure app.auth.sms-http.endpoint.");
        }
        if ("http".equalsIgnoreCase(smsProvider)) {
            requireStrongSecret("APP_AUTH_SMS_HTTP_SECRET", smsHttpSecret, 16, "change-me");
        }
    }

    private void requireStrongSecret(String name, String value, int minLength, String... forbiddenValues) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Production profile requires " + name + ".");
        }
        String trimmed = value.trim();
        if (trimmed.length() < minLength) {
            throw new IllegalStateException("Production profile requires " + name + " to be at least " + minLength + " characters.");
        }
        for (String forbiddenValue : forbiddenValues) {
            if (trimmed.equals(forbiddenValue)) {
                throw new IllegalStateException("Production profile cannot use the default or placeholder value for " + name + ".");
            }
        }
    }

    private void validateConfiguredValue(String name, String value, String placeholderValue) {
        if (value == null || value.isBlank() || value.trim().equals(placeholderValue)) {
            log.warn("Startup check warning: {} is not configured", name);
            return;
        }
        log.info("Startup check passed: {} is configured", name);
    }
}

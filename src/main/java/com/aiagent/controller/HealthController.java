package com.aiagent.controller;

import com.aiagent.config.AiRequestLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final ResourcePatternResolver resourcePatternResolver;
    private final AiRequestLimiter aiRequestLimiter;

    @Value("${spring.application.name:ai-agent}")
    private String applicationName;

    @Value("${spring.ai.dashscope.api-key:}")
    private String dashscopeApiKey;

    @Value("${search-api.api-key:}")
    private String searchApiKey;

    public HealthController(JdbcTemplate jdbcTemplate, ResourcePatternResolver resourcePatternResolver,
                            AiRequestLimiter aiRequestLimiter) {
        this.jdbcTemplate = jdbcTemplate;
        this.resourcePatternResolver = resourcePatternResolver;
        this.aiRequestLimiter = aiRequestLimiter;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return readiness();
    }

    @GetMapping("/live")
    public Map<String, Object> liveness() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "UP");
        body.put("application", applicationName);
        return body;
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> readiness() {
        Map<String, Object> body = new LinkedHashMap<>();
        Map<String, Object> checks = new LinkedHashMap<>();

        boolean databaseUp = checkDatabase(checks);
        boolean documentsUp = checkDocuments(checks);
        checks.put("aiBulkhead", aiBulkheadStatus());
        checks.put("dashscopeApiKeyConfigured", hasText(dashscopeApiKey));
        checks.put("searchApiKeyConfigured", hasText(searchApiKey));

        boolean up = databaseUp && documentsUp;
        body.put("status", up ? "UP" : "DOWN");
        body.put("application", applicationName);
        body.put("checks", checks);

        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private boolean checkDatabase(Map<String, Object> checks) {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            boolean up = Integer.valueOf(1).equals(result);
            checks.put("database", up ? "UP" : "DOWN");
            return up;
        } catch (Exception e) {
            checks.put("database", "DOWN: " + e.getClass().getSimpleName());
            return false;
        }
    }

    private boolean checkDocuments(Map<String, Object> checks) {
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:document/*.md");
            checks.put("ragDocuments", resources.length);
            return resources.length > 0;
        } catch (Exception e) {
            checks.put("ragDocuments", "ERROR: " + e.getClass().getSimpleName());
            return false;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Map<String, Object> aiBulkheadStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("maxConcurrentRequests", aiRequestLimiter.maxConcurrentRequests());
        status.put("inUse", aiRequestLimiter.inUse());
        status.put("available", aiRequestLimiter.availablePermits());
        status.put("queueEnabled", aiRequestLimiter.queueEnabled());
        status.put("queueDepth", aiRequestLimiter.queueDepth());
        status.put("queueCapacity", aiRequestLimiter.queueCapacity());
        return status;
    }
}

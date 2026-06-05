package com.aiagent.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DashScope OpenAI-compatible chat model for models that are not supported by
 * the Spring AI Alibaba text-generation endpoint.
 */
public class DashScopeCompatibleChatModel implements ChatModel {

    private final RestClient restClient;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;
    private final String streamEndpoint;
    private final ChatOptions defaultOptions;

    public DashScopeCompatibleChatModel(String apiKey, String baseUrl, String model, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.defaultOptions = ChatOptions.builder().model(model).build();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.streamEndpoint = stripTrailingSlash(baseUrl) + "/chat/completions";
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        Map<String, Object> request = buildRequest(prompt, false);
        JsonNode response = restClient.post()
                .uri("/chat/completions")
                .body(request)
                .retrieve()
                .body(JsonNode.class);
        return toChatResponse(response);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.<ChatResponse>create(sink -> streamCompatibleResponse(prompt, sink))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public ChatOptions getDefaultOptions() {
        return defaultOptions;
    }

    private void streamCompatibleResponse(Prompt prompt, FluxSink<ChatResponse> sink) {
        try {
            String requestBody = objectMapper.writeValueAsString(buildRequest(prompt, true));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(streamEndpoint))
                    .timeout(Duration.ofMinutes(3))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .header(HttpHeaders.ACCEPT, MediaType.TEXT_EVENT_STREAM_VALUE)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = readAll(response.body());
                sink.error(new IllegalStateException("DashScope stream request failed: HTTP "
                        + response.statusCode() + " - " + body));
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                StringBuilder eventData = new StringBuilder();
                while (!sink.isCancelled() && (line = reader.readLine()) != null) {
                    if (line.isBlank()) {
                        emitStreamEvent(eventData.toString(), sink);
                        eventData.setLength(0);
                        continue;
                    }
                    if (line.startsWith("data:")) {
                        if (!eventData.isEmpty()) {
                            eventData.append('\n');
                        }
                        eventData.append(line.substring(5).trim());
                    }
                }
                if (!eventData.isEmpty() && !sink.isCancelled()) {
                    emitStreamEvent(eventData.toString(), sink);
                }
            }

            if (!sink.isCancelled()) {
                sink.complete();
            }
        } catch (Exception e) {
            if (!sink.isCancelled()) {
                sink.error(e);
            }
        }
    }

    private void emitStreamEvent(String data, FluxSink<ChatResponse> sink) throws Exception {
        if (data == null || data.isBlank() || sink.isCancelled()) {
            return;
        }
        if ("[DONE]".equals(data)) {
            sink.complete();
            return;
        }

        JsonNode response = objectMapper.readTree(data);
        if (response.has("error")) {
            sink.error(new IllegalStateException(response.path("error").toString()));
            return;
        }

        JsonNode choice = response.path("choices").isArray() && !response.path("choices").isEmpty()
                ? response.path("choices").get(0)
                : objectMapper.createObjectNode();
        JsonNode delta = choice.path("delta");
        String content = delta.path("content").isMissingNode() || delta.path("content").isNull()
                ? ""
                : delta.path("content").asText("");
        if (content.isEmpty()) {
            return;
        }

        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(choice.path("finish_reason").asText(""))
                .build();
        ChatResponseMetadata responseMetadata = ChatResponseMetadata.builder()
                .id(response.path("id").asText(""))
                .model(response.path("model").asText(model))
                .build();
        sink.next(new ChatResponse(
                List.of(new Generation(new AssistantMessage(content), generationMetadata)),
                responseMetadata
        ));
    }

    private String readAll(InputStream inputStream) throws Exception {
        try (InputStream in = inputStream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Map<String, Object> buildRequest(Prompt prompt, boolean stream) {
        ChatOptions options = prompt.getOptions() == null ? defaultOptions : prompt.getOptions();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", firstNonBlank(options.getModel(), model));
        request.put("messages", prompt.getInstructions().stream().map(this::toCompatibleMessage).toList());
        request.put("stream", stream);
        putIfPresent(request, "temperature", options.getTemperature());
        putIfPresent(request, "top_p", options.getTopP());
        putIfPresent(request, "max_tokens", options.getMaxTokens());
        putIfPresent(request, "presence_penalty", options.getPresencePenalty());
        putIfPresent(request, "frequency_penalty", options.getFrequencyPenalty());
        if (options.getStopSequences() != null && !options.getStopSequences().isEmpty()) {
            request.put("stop", options.getStopSequences());
        }
        if (options instanceof ToolCallingChatOptions toolOptions
                && toolOptions.getToolCallbacks() != null
                && !toolOptions.getToolCallbacks().isEmpty()) {
            request.put("tools", toolOptions.getToolCallbacks().stream().map(this::toCompatibleTool).toList());
            request.put("tool_choice", "auto");
        }
        return request;
    }

    private Map<String, Object> toCompatibleMessage(Message message) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (message instanceof ToolResponseMessage toolResponseMessage) {
            ToolResponseMessage.ToolResponse response = toolResponseMessage.getResponses().isEmpty()
                    ? null
                    : toolResponseMessage.getResponses().get(0);
            item.put("role", "tool");
            item.put("content", response == null ? "" : response.responseData());
            if (response != null) {
                item.put("tool_call_id", response.id());
                item.put("name", response.name());
            }
            return item;
        }

        if (message instanceof AssistantMessage assistantMessage) {
            item.put("role", "assistant");
            item.put("content", assistantMessage.getText());
            if (!assistantMessage.getToolCalls().isEmpty()) {
                item.put("tool_calls", assistantMessage.getToolCalls().stream().map(toolCall -> {
                    Map<String, Object> function = new LinkedHashMap<>();
                    function.put("name", toolCall.name());
                    function.put("arguments", toolCall.arguments());
                    Map<String, Object> compatibleToolCall = new LinkedHashMap<>();
                    compatibleToolCall.put("id", toolCall.id());
                    compatibleToolCall.put("type", firstNonBlank(toolCall.type(), "function"));
                    compatibleToolCall.put("function", function);
                    return compatibleToolCall;
                }).toList());
            }
            return item;
        }

        MessageType type = message.getMessageType();
        if (MessageType.SYSTEM.equals(type)) {
            item.put("role", "system");
        } else if (MessageType.ASSISTANT.equals(type)) {
            item.put("role", "assistant");
        } else {
            item.put("role", "user");
        }
        item.put("content", message.getText());
        return item;
    }

    private Map<String, Object> toCompatibleTool(ToolCallback toolCallback) {
        ToolDefinition definition = toolCallback.getToolDefinition();
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", definition.name());
        function.put("description", definition.description());
        function.put("parameters", parseSchema(definition.inputSchema()));
        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private Object parseSchema(String schema) {
        if (schema == null || schema.isBlank()) {
            return Map.of("type", "object", "properties", Map.of());
        }
        try {
            return objectMapper.readValue(schema, Object.class);
        } catch (Exception ignored) {
            return Map.of("type", "object", "properties", Map.of());
        }
    }

    private ChatResponse toChatResponse(JsonNode response) {
        JsonNode choice = response.path("choices").isArray() && !response.path("choices").isEmpty()
                ? response.path("choices").get(0)
                : objectMapper.createObjectNode();
        JsonNode message = choice.path("message");
        String content = message.path("content").isMissingNode() || message.path("content").isNull()
                ? ""
                : message.path("content").asText("");
        List<AssistantMessage.ToolCall> toolCalls = new ArrayList<>();
        JsonNode toolCallNodes = message.path("tool_calls");
        if (toolCallNodes.isArray()) {
            for (JsonNode toolCallNode : toolCallNodes) {
                JsonNode function = toolCallNode.path("function");
                toolCalls.add(new AssistantMessage.ToolCall(
                        toolCallNode.path("id").asText(""),
                        toolCallNode.path("type").asText("function"),
                        function.path("name").asText(""),
                        function.path("arguments").asText("{}")
                ));
            }
        }

        AssistantMessage assistantMessage = new AssistantMessage(content, Map.of(), toolCalls);
        ChatGenerationMetadata generationMetadata = ChatGenerationMetadata.builder()
                .finishReason(choice.path("finish_reason").asText(""))
                .build();
        ChatResponseMetadata responseMetadata = ChatResponseMetadata.builder()
                .id(response.path("id").asText(""))
                .model(response.path("model").asText(model))
                .usage(new DefaultUsage(
                        nullIfMissing(response.path("usage").path("prompt_tokens")),
                        nullIfMissing(response.path("usage").path("completion_tokens")),
                        nullIfMissing(response.path("usage").path("total_tokens")),
                        response.path("usage")
                ))
                .build();
        return new ChatResponse(List.of(new Generation(assistantMessage, generationMetadata)), responseMetadata);
    }

    private Integer nullIfMissing(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asInt();
    }

    private void putIfPresent(Map<String, Object> request, String key, Object value) {
        if (value != null) {
            request.put(key, value);
        }
    }

    private String firstNonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}

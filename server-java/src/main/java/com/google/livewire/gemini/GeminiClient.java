package com.google.livewire.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Minimal client for Google Gemini REST API.
 */
@Component
public class GeminiClient {
    private final WebClient webClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String model;

    public GeminiClient(WebClient.Builder builder,
                        @Value("${gemini.apiKey}") String apiKey,
                        @Value("${gemini.model}") String model) {
        this.model = model;
        this.webClient = builder.baseUrl("https://generativelanguage.googleapis.com")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    public Mono<JsonNode> generateContent(List<JsonNode> history, String text) {
        ObjectNode request = mapper.createObjectNode();
        ArrayNode contents = request.putArray("contents");
        for (JsonNode node : history) {
            contents.add(node);
        }
        ObjectNode user = mapper.createObjectNode();
        user.put("role", "user");
        ArrayNode parts = user.putArray("parts");
        ObjectNode part = mapper.createObjectNode();
        part.put("text", text);
        parts.add(part);
        contents.add(user);

        return webClient.post()
                .uri("/v1beta/models/" + model + ":generateContent")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> sendToolResponse(List<JsonNode> history, String name, JsonNode result) {
        ObjectNode request = mapper.createObjectNode();
        ArrayNode contents = request.putArray("contents");
        for (JsonNode node : history) {
            contents.add(node);
        }
        ObjectNode toolResponse = mapper.createObjectNode();
        toolResponse.put("role", "tool");
        ArrayNode parts = toolResponse.putArray("parts");
        ObjectNode part = mapper.createObjectNode();
        ObjectNode functionResult = mapper.createObjectNode();
        functionResult.put("name", name);
        functionResult.set("response", result);
        part.set("functionResponse", functionResult);
        parts.add(part);
        contents.add(toolResponse);

        return webClient.post()
                .uri("/v1beta/models/" + model + ":generateContent")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }
}

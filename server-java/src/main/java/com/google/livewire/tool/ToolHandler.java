package com.google.livewire.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

/**
 * Executes external tools via HTTP cloud functions.
 */
@Component
public class ToolHandler {
    private final WebClient webClient;
    private final Map<String, String> cloudFunctions;
    private final ObjectMapper mapper = new ObjectMapper();

    public ToolHandler(WebClient.Builder builder,
                       @Value("${tool.weather.url:}") String weatherUrl) {
        this.webClient = builder.build();
        this.cloudFunctions = Map.of("get_weather", weatherUrl);
    }

    public Mono<JsonNode> executeTool(String name, Map<String, String> params) {
        String baseUrl = cloudFunctions.get(name);
        if (baseUrl == null || baseUrl.isEmpty()) {
            ObjectNode error = mapper.createObjectNode();
            error.put("error", "Unknown tool: " + name);
            return Mono.just(error);
        }
        URI uri = URI.create(baseUrl);
        WebClient.RequestHeadersSpec<?> spec = webClient.get()
            .uri(builder -> {
                builder = builder.scheme(uri.getScheme())
                        .host(uri.getHost())
                        .path(uri.getPath());
                params.forEach(builder::queryParam);
                return builder.build();
            })
            .accept(MediaType.APPLICATION_JSON);
        return spec.retrieve()
            .bodyToMono(JsonNode.class)
            .onErrorResume(e -> {
                ObjectNode error = mapper.createObjectNode();
                error.put("error", "Tool execution failed: " + e.getMessage());
                return Mono.just(error);
            });
    }
}

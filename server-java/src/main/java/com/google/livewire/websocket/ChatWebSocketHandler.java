package com.google.livewire.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.livewire.gemini.GeminiClient;
import com.google.livewire.session.SessionManager;
import com.google.livewire.session.SessionState;
import com.google.livewire.tool.ToolHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket handler that forwards messages to Gemini and returns responses.
 */
@Component
public class ChatWebSocketHandler implements WebSocketHandler {
    private final ObjectMapper mapper = new ObjectMapper();
    private final GeminiClient geminiClient;
    private final SessionManager sessionManager;
    private final ToolHandler toolHandler;

    public ChatWebSocketHandler(GeminiClient geminiClient,
                                SessionManager sessionManager,
                                ToolHandler toolHandler) {
        this.geminiClient = geminiClient;
        this.sessionManager = sessionManager;
        this.toolHandler = toolHandler;
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String id = session.getId();
        SessionState state = sessionManager.createSession(id);

        Mono<Void> ready = session.send(Mono.just(session.textMessage("{\"ready\":true}")));

        Flux<WebSocketMessage> responses = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(payload -> handleClientMessage(payload, session, state));

        return ready.thenMany(session.send(responses))
                .doFinally(sig -> sessionManager.removeSession(id))
                .then();
    }

    private Flux<WebSocketMessage> handleClientMessage(String payload, WebSocketSession session, SessionState state) {
        try {
            JsonNode root = mapper.readTree(payload);
            String type = root.path("type").asText();
            if ("text".equals(type)) {
                String text = root.path("data").asText();
                state.getHistory().add(buildUserMessage(text));
                return geminiClient.generateContent(state.getHistory(), text)
                        .flatMapMany(res -> processGeminiResponse(res, session, state));
            }
        } catch (Exception e) {
            ObjectNode err = error("Invalid message", "Check format");
            return Flux.just(session.textMessage(err.toString()));
        }
        return Flux.empty();
    }

    private Flux<WebSocketMessage> processGeminiResponse(JsonNode res, WebSocketSession session, SessionState state) {
        JsonNode candidates = res.path("candidates");
        if (candidates.isArray() && !candidates.isEmpty()) {
            ArrayNode parts = (ArrayNode) candidates.get(0).path("content").path("parts");
            return Flux.fromIterable(parts)
                    .flatMap(part -> {
                        if (part.has("functionCall")) {
                            JsonNode func = part.get("functionCall");
                            String name = func.path("name").asText();
                            Map<String, String> params = new HashMap<>();
                            func.path("args").fields().forEachRemaining(e -> params.put(e.getKey(), e.getValue().asText()));
                            return toolHandler.executeTool(name, params)
                                    .flatMapMany(result -> {
                                        state.getHistory().add(buildFunctionResponse(name, result));
                                        ObjectNode callMsg = mapper.createObjectNode();
                                        callMsg.put("type", "function_call");
                                        callMsg.set("data", func);
                                        ObjectNode respMsg = mapper.createObjectNode();
                                        respMsg.put("type", "function_response");
                                        respMsg.set("data", result);
                                        return Flux.concat(
                                                Mono.just(session.textMessage(callMsg.toString())),
                                                Mono.just(session.textMessage(respMsg.toString())),
                                                geminiClient.sendToolResponse(state.getHistory(), name, result)
                                                        .flatMapMany(r -> processGeminiResponse(r, session, state))
                                        );
                                    });
                        } else if (part.has("text")) {
                            String text = part.get("text").asText();
                            state.getHistory().add(buildModelMessage(text));
                            ObjectNode msg = mapper.createObjectNode();
                            msg.put("type", "text");
                            msg.put("data", text);
                            return Flux.just(session.textMessage(msg.toString()));
                        }
                        return Flux.empty();
                    });
        }
        return Flux.empty();
    }

    private ObjectNode buildUserMessage(String text) {
        ObjectNode node = mapper.createObjectNode();
        node.put("role", "user");
        ArrayNode parts = node.putArray("parts");
        ObjectNode p = mapper.createObjectNode();
        p.put("text", text);
        parts.add(p);
        return node;
    }

    private JsonNode buildModelMessage(String text) {
        ObjectNode node = mapper.createObjectNode();
        node.put("role", "model");
        ArrayNode parts = node.putArray("parts");
        ObjectNode p = mapper.createObjectNode();
        p.put("text", text);
        parts.add(p);
        return node;
    }

    private JsonNode buildFunctionResponse(String name, JsonNode response) {
        ObjectNode node = mapper.createObjectNode();
        node.put("role", "tool");
        ArrayNode parts = node.putArray("parts");
        ObjectNode p = mapper.createObjectNode();
        ObjectNode fr = mapper.createObjectNode();
        fr.put("name", name);
        fr.set("response", response);
        p.set("functionResponse", fr);
        parts.add(p);
        return node;
    }

    private ObjectNode error(String message, String action) {
        ObjectNode data = mapper.createObjectNode();
        data.put("message", message);
        data.put("action", action);
        data.put("error_type", "general");
        ObjectNode wrapper = mapper.createObjectNode();
        wrapper.put("type", "error");
        wrapper.set("data", data);
        return wrapper;
    }
}

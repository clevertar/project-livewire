package com.google.livewire.session;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages SessionState objects for active WebSocket clients.
 */
@Component
public class SessionManager {
    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public SessionState createSession(String sessionId) {
        SessionState state = new SessionState();
        sessions.put(sessionId, state);
        return state;
    }

    public SessionState getSession(String sessionId) {
        return sessions.get(sessionId);
    }

    public void removeSession(String sessionId) {
        sessions.remove(sessionId);
    }
}

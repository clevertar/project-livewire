package com.google.livewire.session;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks the state of a client session.
 */
public class SessionState {
    private final List<JsonNode> history = new ArrayList<>();
    private boolean receivingResponse = false;
    private boolean interrupted = false;

    public List<JsonNode> getHistory() {
        return history;
    }

    public boolean isReceivingResponse() {
        return receivingResponse;
    }

    public void setReceivingResponse(boolean receivingResponse) {
        this.receivingResponse = receivingResponse;
    }

    public boolean isInterrupted() {
        return interrupted;
    }

    public void setInterrupted(boolean interrupted) {
        this.interrupted = interrupted;
    }
}

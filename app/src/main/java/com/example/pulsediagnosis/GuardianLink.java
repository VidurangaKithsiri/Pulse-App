package com.example.pulsediagnosis;

public class GuardianLink {
    private boolean connected;
    private long connectedAt;

    public GuardianLink() {
    }

    public GuardianLink(
            boolean connected,
            long connectedAt
    ) {
        this.connected = connected;
        this.connectedAt = connectedAt;
    }

    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public long getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(long connectedAt) {
        this.connectedAt = connectedAt;
    }
}

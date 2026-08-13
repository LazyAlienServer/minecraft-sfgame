package com.sfgame.game;

import java.util.UUID;

public final class PlayerMatchState {
    private final UUID playerId;
    private String currentClass;
    private String pendingClass;
    private TeamSide cachedSide = TeamSide.NONE;
    private boolean participating;
    private boolean queued;
    private boolean pendingImmediateJoin;
    private boolean respawning;
    private int respawnTicks;
    private int protectionTicks;
    private int kills;
    private int deaths;
    private boolean connected;

    public PlayerMatchState(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID playerId() { return playerId; }
    public String currentClass() { return currentClass; }
    public void currentClass(String value) { currentClass = value; }
    public String pendingClass() { return pendingClass; }
    public void pendingClass(String value) { pendingClass = value; }
    public TeamSide cachedSide() { return cachedSide; }
    public void cachedSide(TeamSide value) { cachedSide = value; }
    public boolean participating() { return participating; }
    public void participating(boolean value) { participating = value; }
    public boolean queued() { return queued; }
    public void queued(boolean value) { queued = value; }
    public boolean pendingImmediateJoin() { return pendingImmediateJoin; }
    public void pendingImmediateJoin(boolean value) { pendingImmediateJoin = value; }
    public boolean respawning() { return respawning; }
    public void respawning(boolean value) { respawning = value; }
    public int respawnTicks() { return respawnTicks; }
    public void respawnTicks(int value) { respawnTicks = value; }
    public int protectionTicks() { return protectionTicks; }
    public void protectionTicks(int value) { protectionTicks = value; }
    public int kills() { return kills; }
    public void addKill() { kills++; }
    public int deaths() { return deaths; }
    public void addDeath() { deaths++; }
    public boolean connected() { return connected; }
    public void connected(boolean value) { connected = value; }

    public void resetRoundStats() {
        kills = 0;
        deaths = 0;
        respawning = false;
        respawnTicks = 0;
        protectionTicks = 0;
        pendingImmediateJoin = false;
    }
}


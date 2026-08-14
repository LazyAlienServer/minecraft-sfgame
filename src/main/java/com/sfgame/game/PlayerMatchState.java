package com.sfgame.game;

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

public final class PlayerMatchState {
    private final UUID playerId;
    private final Map<String, String> currentClasses = new HashMap<>();
    private final Map<String, String> pendingClasses = new HashMap<>();
    private final Map<String, String> currentCaptainClasses = new HashMap<>();
    private final Map<String, String> pendingCaptainClasses = new HashMap<>();
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
    public String currentClass(String modeId) { return currentClasses.get(modeId); }
    public void currentClass(String modeId, String value) { putOrRemove(currentClasses, modeId, value); }
    public String pendingClass(String modeId) { return pendingClasses.get(modeId); }
    public void pendingClass(String modeId, String value) { putOrRemove(pendingClasses, modeId, value); }
    public String currentCaptainClass(String modeId) { return currentCaptainClasses.get(modeId); }
    public void currentCaptainClass(String modeId, String value) { putOrRemove(currentCaptainClasses, modeId, value); }
    public String pendingCaptainClass(String modeId) { return pendingCaptainClasses.get(modeId); }
    public void pendingCaptainClass(String modeId, String value) { putOrRemove(pendingCaptainClasses, modeId, value); }
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

    private static void putOrRemove(Map<String, String> map, String key, String value) {
        if (value == null) map.remove(key); else map.put(key, value);
    }
}

package com.sfgame.game;

public final class CapturePointState {
    public enum Change { NONE, NEUTRALIZED, CAPTURED }

    private TeamSide owner = TeamSide.NONE;
    private TeamSide contender = TeamSide.NONE;
    private double progress;
    private boolean contested;

    public TeamSide owner() { return owner; }
    public TeamSide contender() { return contender; }
    public double progress() { return progress; }
    public boolean contested() { return contested; }
    public void contested(boolean value) { contested = value; }

    public Change advance(TeamSide leader, double delta, boolean empty) {
        contested = false;
        delta = Math.max(0.0, delta);
        if (empty) {
            if (owner == TeamSide.NONE && progress > 0.0) {
                progress = Math.max(0.0, progress - delta);
                if (progress == 0.0) contender = TeamSide.NONE;
            }
            return Change.NONE;
        }
        if (leader == TeamSide.NONE || delta == 0.0) return Change.NONE;

        if (owner == TeamSide.NONE) {
            if (contender == TeamSide.NONE) contender = leader;
            if (contender != leader) {
                progress = Math.max(0.0, progress - delta);
                if (progress == 0.0) contender = leader;
                return Change.NONE;
            }
            progress = Math.min(1.0, progress + delta);
            if (progress >= 1.0) {
                owner = leader; contender = TeamSide.NONE; progress = 1.0;
                return Change.CAPTURED;
            }
            return Change.NONE;
        }

        if (leader == owner) {
            progress = Math.min(1.0, progress + delta);
            if (progress >= 1.0) contender = TeamSide.NONE;
            return Change.NONE;
        }

        contender = leader;
        progress = Math.max(0.0, progress - delta);
        if (progress <= 0.0) {
            owner = TeamSide.NONE; progress = 0.0;
            return Change.NEUTRALIZED;
        }
        return Change.NONE;
    }

    public void reset() {
        owner = TeamSide.NONE; contender = TeamSide.NONE; progress = 0.0; contested = false;
    }

    public void reset(TeamSide initialOwner) {
        owner = initialOwner == null ? TeamSide.NONE : initialOwner;
        contender = TeamSide.NONE;
        progress = owner == TeamSide.NONE ? 0.0 : 1.0;
        contested = false;
    }
}

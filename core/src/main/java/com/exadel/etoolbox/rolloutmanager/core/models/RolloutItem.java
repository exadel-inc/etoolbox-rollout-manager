package com.exadel.etoolbox.rolloutmanager.core.models;

public class RolloutItem {
    private String master;
    private String target;
    private int depth;
    boolean autoRolloutTrigger;

    public String getMaster() {
        return master;
    }

    public String getTarget() {
        return target;
    }

    public int getDepth() {
        return depth;
    }

    public boolean isAutoRolloutTrigger() {
        return autoRolloutTrigger;
    }
}

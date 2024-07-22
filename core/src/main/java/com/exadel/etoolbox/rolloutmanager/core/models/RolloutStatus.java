package com.exadel.etoolbox.rolloutmanager.core.models;

public class RolloutStatus {
    private boolean isSuccess;
    private final String target;

    public RolloutStatus(String target) {
        this.target = target;
    }

    public boolean isSuccess() {
        return isSuccess;
    }

    public void setSuccess(boolean success) {
        isSuccess = success;
    }

    public String getTarget() {
        return target;
    }
}

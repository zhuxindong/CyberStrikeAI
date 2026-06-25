package com.cyberstrike.dto;

import java.util.List;

public class HITLRequest {
    private Boolean enabled;
    private String mode;
    private List<String> sensitiveTools;
    private Integer timeoutSeconds;

    public HITLRequest() {
    }

    public HITLRequest(Boolean enabled, String mode, List<String> sensitiveTools, Integer timeoutSeconds) {
        this.enabled = enabled;
        this.mode = mode;
        this.sensitiveTools = sensitiveTools;
        this.timeoutSeconds = timeoutSeconds;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<String> getSensitiveTools() {
        return sensitiveTools;
    }

    public void setSensitiveTools(List<String> sensitiveTools) {
        this.sensitiveTools = sensitiveTools;
    }

    public Integer getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(Integer timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}

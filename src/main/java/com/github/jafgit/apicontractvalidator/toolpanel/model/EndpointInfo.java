package com.github.jafgit.apicontractvalidator.toolpanel.model;

public class EndpointInfo {
    private final String path;
    private final String method;
    private EndpointStatus status;

    public EndpointInfo(String path, String method, EndpointStatus status) {
        this.path = path;
        this.method = method;
        this.status = status;
    }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }

    public EndpointStatus getStatus() {
        return status;
    }

    public void setStatus(EndpointStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        // This is a fallback, the custom renderer will be used primarily
        return method + " " + path;
    }
}

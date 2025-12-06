package com.github.jafgit.apicontractvalidator.toolpanel.model;

public class EndpointInfo {
    private final String path;
    private final String method;
    private EndpointStatus status;
    private final String tag; // New field for grouping

    public EndpointInfo(String path, String method, EndpointStatus status, String tag) {
        this.path = path;
        this.method = method;
        this.status = status;
        this.tag = tag;
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

    public String getTag() {
        return tag;
    }

    @Override
    public String toString() {
        return method + " " + path;
    }
}

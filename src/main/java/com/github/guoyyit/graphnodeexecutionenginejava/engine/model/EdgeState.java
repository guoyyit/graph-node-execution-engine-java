package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

/**
 * 边在某次运行中的解析状态。
 */
public class EdgeState {

    private String edgeId;
    private String source;
    private String target;
    private EdgeStatus status = EdgeStatus.PENDING;

    public EdgeState() {
    }

    public EdgeState(String edgeId, String source, String target) {
        this.edgeId = edgeId;
        this.source = source;
        this.target = target;
    }

    public String getEdgeId() {
        return edgeId;
    }

    public void setEdgeId(String edgeId) {
        this.edgeId = edgeId;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public EdgeStatus getStatus() {
        return status;
    }

    public void setStatus(EdgeStatus status) {
        this.status = status;
    }
}

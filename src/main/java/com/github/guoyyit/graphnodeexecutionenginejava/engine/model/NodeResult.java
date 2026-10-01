package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** 节点在一次运行中的可见结果，随运行快照持久化。 */
public class NodeResult {

    private NodeStatus status = NodeStatus.PENDING;
    private Map<String, Object> output = new LinkedHashMap<>();
    private String error;
    private int attempts;
    private Long startedAtEpochMs;
    private Long finishedAtEpochMs;

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Long getStartedAtEpochMs() {
        return startedAtEpochMs;
    }

    public void setStartedAtEpochMs(Long startedAtEpochMs) {
        this.startedAtEpochMs = startedAtEpochMs;
    }

    public Long getFinishedAtEpochMs() {
        return finishedAtEpochMs;
    }

    public void setFinishedAtEpochMs(Long finishedAtEpochMs) {
        this.finishedAtEpochMs = finishedAtEpochMs;
    }
}

package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;

/**
 * 节点在某次运行中的运行时状态。
 */
public class NodeState {

    private String nodeId;
    private NodeStatus status = NodeStatus.PENDING;
    private int attempts;
    private String lastError;
    private Instant startedAt;
    private Instant finishedAt;

    public NodeState() {
    }

    public NodeState(String nodeId) {
        this.nodeId = nodeId;
    }

    @JsonIgnore
    public boolean isTerminal() {
        return status.isTerminal();
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }
}

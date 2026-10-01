package com.github.guoyyit.graphnodeexecutionenginejava.engine.web.dto;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.FailureStrategy;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeState;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunState;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunStatus;

import java.time.Instant;
import java.util.Map;

/**
 * 运行状态响应。
 */
public class RunResponse {

    private String runId;
    private String definitionName;
    private int definitionVersion;
    private RunStatus status;
    private FailureStrategy failureStrategy;
    private int concurrency;
    private Map<String, NodeState> nodeStates;
    private Map<String, Map<String, Object>> outputs;
    private String error;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;

    public static RunResponse from(RunState run) {
        RunResponse response = new RunResponse();
        response.runId = run.getRunId();
        response.definitionName = run.getDefinitionName();
        response.definitionVersion = run.getDefinitionVersion();
        response.status = run.getStatus();
        response.failureStrategy = run.getFailureStrategy();
        response.concurrency = run.getConcurrency();
        response.nodeStates = run.getNodeStates();
        response.outputs = run.getOutputs();
        response.error = run.getError();
        response.createdAt = run.getCreatedAt();
        response.startedAt = run.getStartedAt();
        response.finishedAt = run.getFinishedAt();
        return response;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getDefinitionName() {
        return definitionName;
    }

    public void setDefinitionName(String definitionName) {
        this.definitionName = definitionName;
    }

    public int getDefinitionVersion() {
        return definitionVersion;
    }

    public void setDefinitionVersion(int definitionVersion) {
        this.definitionVersion = definitionVersion;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public FailureStrategy getFailureStrategy() {
        return failureStrategy;
    }

    public void setFailureStrategy(FailureStrategy failureStrategy) {
        this.failureStrategy = failureStrategy;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public Map<String, NodeState> getNodeStates() {
        return nodeStates;
    }

    public void setNodeStates(Map<String, NodeState> nodeStates) {
        this.nodeStates = nodeStates;
    }

    public Map<String, Map<String, Object>> getOutputs() {
        return outputs;
    }

    public void setOutputs(Map<String, Map<String, Object>> outputs) {
        this.outputs = outputs;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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

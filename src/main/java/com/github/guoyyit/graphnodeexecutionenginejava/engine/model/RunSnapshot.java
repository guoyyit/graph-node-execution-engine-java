package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运行快照：运行状态的可持久化表示。
 * 内嵌提交时刻的定义副本，保证定义后续修改不影响已开始运行。
 */
public class RunSnapshot {

    private String runId;
    private String definitionId;
    private String definitionVersion;
    private RunStatus status = RunStatus.RUNNING;
    private WorkflowDefinition definition;
    private Map<String, Object> input = new LinkedHashMap<>();
    private Map<String, NodeResult> nodeResults = new LinkedHashMap<>();
    private String failureReason;
    /** 取消请求标记，随快照持久化，崩溃恢复后继续执行取消语义。 */
    private boolean cancelRequested;
    private long createdAtEpochMs;
    private long updatedAtEpochMs;

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public String getDefinitionVersion() {
        return definitionVersion;
    }

    public void setDefinitionVersion(String definitionVersion) {
        this.definitionVersion = definitionVersion;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    public WorkflowDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(WorkflowDefinition definition) {
        this.definition = definition;
    }

    public Map<String, Object> getInput() {
        return input;
    }

    public void setInput(Map<String, Object> input) {
        this.input = input;
    }

    public Map<String, NodeResult> getNodeResults() {
        return nodeResults;
    }

    public void setNodeResults(Map<String, NodeResult> nodeResults) {
        this.nodeResults = nodeResults;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public void setCancelRequested(boolean cancelRequested) {
        this.cancelRequested = cancelRequested;
    }

    public long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }

    public void setCreatedAtEpochMs(long createdAtEpochMs) {
        this.createdAtEpochMs = createdAtEpochMs;
    }

    public long getUpdatedAtEpochMs() {
        return updatedAtEpochMs;
    }

    public void setUpdatedAtEpochMs(long updatedAtEpochMs) {
        this.updatedAtEpochMs = updatedAtEpochMs;
    }
}

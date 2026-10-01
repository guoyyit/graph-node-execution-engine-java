package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一次运行的完整状态，会被持久化到磁盘并在重启后恢复。
 */
public class RunState {

    private String runId;
    private String definitionName;
    private int definitionVersion;
    private GraphDefinition definition;
    private volatile RunStatus status = RunStatus.PENDING;
    private FailureStrategy failureStrategy = FailureStrategy.ABORT;
    private int concurrency = 1;

    /** 按节点声明顺序保存 */
    private Map<String, NodeState> nodeStates = new LinkedHashMap<>();
    /** 按边声明顺序保存，key 为 edgeId */
    private Map<String, EdgeState> edgeStates = new LinkedHashMap<>();
    /** 已提交的节点输出，key 为节点 id */
    private Map<String, Map<String, Object>> outputs = new LinkedHashMap<>();

    private String error;
    private Instant createdAt;
    private Instant startedAt;
    private Instant finishedAt;
    private volatile boolean cancelRequested;

    public RunState() {
    }

    public RunState(String runId, GraphDefinition definition, FailureStrategy failureStrategy, int concurrency) {
        this.runId = runId;
        this.definition = definition;
        this.definitionName = definition.name();
        this.definitionVersion = definition.version();
        this.failureStrategy = failureStrategy;
        this.concurrency = concurrency;
        this.createdAt = Instant.now();
        for (NodeDefinition node : definition.nodes()) {
            this.nodeStates.put(node.id(), new NodeState(node.id()));
        }
        for (EdgeDefinition edge : definition.edges()) {
            this.edgeStates.put(edge.edgeId(),
                    new EdgeState(edge.edgeId(), edge.source(), edge.target()));
        }
    }

    public NodeState nodeState(String nodeId) {
        return nodeStates.get(nodeId);
    }

    public EdgeState edgeState(String edgeId) {
        return edgeStates.get(edgeId);
    }

    public List<EdgeState> incomingEdges(String nodeId) {
        List<EdgeState> result = new ArrayList<>();
        for (EdgeState edge : edgeStates.values()) {
            if (edge.getTarget().equals(nodeId)) {
                result.add(edge);
            }
        }
        return result;
    }

    public List<EdgeState> outgoingEdges(String nodeId) {
        List<EdgeState> result = new ArrayList<>();
        for (EdgeState edge : edgeStates.values()) {
            if (edge.getSource().equals(nodeId)) {
                result.add(edge);
            }
        }
        return result;
    }

    public void putOutput(String nodeId, Map<String, Object> data) {
        outputs.put(nodeId, data);
    }

    @JsonIgnore
    public boolean allNodesTerminal() {
        for (NodeState state : nodeStates.values()) {
            if (!state.isTerminal()) {
                return false;
            }
        }
        return true;
    }

    @JsonIgnore
    public boolean hasFailure() {
        for (NodeState state : nodeStates.values()) {
            if (state.getStatus() == NodeStatus.FAILED) {
                return true;
            }
        }
        return false;
    }

    @JsonIgnore
    public long runningNodeCount() {
        return nodeStates.values().stream()
                .filter(s -> s.getStatus() == NodeStatus.RUNNING)
                .count();
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

    public GraphDefinition getDefinition() {
        return definition;
    }

    public void setDefinition(GraphDefinition definition) {
        this.definition = definition;
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

    public Map<String, EdgeState> getEdgeStates() {
        return edgeStates;
    }

    public void setEdgeStates(Map<String, EdgeState> edgeStates) {
        this.edgeStates = edgeStates;
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

    public boolean isCancelRequested() {
        return cancelRequested;
    }

    public void setCancelRequested(boolean cancelRequested) {
        this.cancelRequested = cancelRequested;
    }
}

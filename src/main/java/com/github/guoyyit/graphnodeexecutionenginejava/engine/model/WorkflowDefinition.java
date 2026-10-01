package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

import java.util.List;

/**
 * 工作流执行定义（可版本化，提交后不可变）。
 *
 * @param id      定义 id
 * @param version 定义版本，同一 id 下不同版本可并存
 * @param nodes   节点列表
 * @param edges   有向连线列表
 */
public record WorkflowDefinition(
        String id,
        String version,
        List<NodeDefinition> nodes,
        List<EdgeDefinition> edges) {

    public WorkflowDefinition {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    public NodeDefinition node(String nodeId) {
        return nodes.stream()
                .filter(n -> n.id().equals(nodeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown node: " + nodeId));
    }
}

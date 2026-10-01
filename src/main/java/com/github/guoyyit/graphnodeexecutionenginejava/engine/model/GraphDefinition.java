package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

import java.util.List;

/**
 * 工作流执行定义（版本化）。
 *
 * @param name    定义名称
 * @param version 版本号，同一名称下单调递增
 * @param nodes   节点列表（保持声明顺序）
 * @param edges   连线列表
 */
public record GraphDefinition(
        String name,
        int version,
        List<NodeDefinition> nodes,
        List<EdgeDefinition> edges
) {
    public GraphDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("definition name must not be blank");
        }
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("definition must contain at least one node");
        }
        if (edges == null) {
            edges = List.of();
        }
    }
}

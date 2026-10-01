package com.github.guoyyit.graphnodeexecutionenginejava.engine.exec;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeDefinition;

import java.util.Map;

/**
 * 节点执行上下文。
 *
 * @param runId     所属运行 id
 * @param node      节点定义
 * @param attempt   当前是第几次尝试（从 1 开始）
 * @param inputs    前置节点输出，key 为前置节点 id
 */
public record NodeContext(
        String runId,
        NodeDefinition node,
        int attempt,
        Map<String, Map<String, Object>> inputs
) {
    public String nodeId() {
        return node.id();
    }
}

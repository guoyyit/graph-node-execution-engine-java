package com.github.guoyyit.graphnodeexecutionenginejava.engine.def;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.EdgeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.GraphDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeDefinition;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 定义校验：重复节点 id、悬空连线、环。
 * 校验针对整张图整体进行，失败时不会产生任何执行。
 */
public class DefinitionValidator {

    public void validate(GraphDefinition definition) {
        Set<String> seen = new HashSet<>();
        for (NodeDefinition node : definition.nodes()) {
            if (!seen.add(node.id())) {
                throw new DefinitionException(DefinitionException.Reason.DUPLICATE_NODE_ID,
                        "duplicate node id: " + node.id());
            }
        }

        Map<String, List<String>> adjacency = new HashMap<>();
        for (EdgeDefinition edge : definition.edges()) {
            if (!seen.contains(edge.source())) {
                throw new DefinitionException(DefinitionException.Reason.NODE_NOT_FOUND,
                        "edge source '" + edge.source() + "' does not exist");
            }
            if (!seen.contains(edge.target())) {
                throw new DefinitionException(DefinitionException.Reason.NODE_NOT_FOUND,
                        "edge target '" + edge.target() + "' does not exist");
            }
            adjacency.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge.target());
        }

        detectCycle(definition.nodes().stream().map(NodeDefinition::id).toList(), adjacency);
    }

    /**
     * 三色标记法 DFS 检测有向环。
     */
    private void detectCycle(List<String> nodeIds, Map<String, List<String>> adjacency) {
        Map<String, Integer> color = new HashMap<>(); // 0=white, 1=gray, 2=black
        for (String id : nodeIds) {
            color.put(id, 0);
        }
        for (String id : nodeIds) {
            if (color.get(id) == 0 && dfs(id, adjacency, color)) {
                throw new DefinitionException(DefinitionException.Reason.CYCLE_DETECTED,
                        "cycle detected involving node: " + id);
            }
        }
    }

    private boolean dfs(String node, Map<String, List<String>> adjacency, Map<String, Integer> color) {
        color.put(node, 1);
        for (String next : adjacency.getOrDefault(node, List.of())) {
            Integer c = color.get(next);
            if (c == null) {
                continue;
            }
            if (c == 1) {
                return true;
            }
            if (c == 0 && dfs(next, adjacency, color)) {
                return true;
            }
        }
        color.put(node, 2);
        return false;
    }
}

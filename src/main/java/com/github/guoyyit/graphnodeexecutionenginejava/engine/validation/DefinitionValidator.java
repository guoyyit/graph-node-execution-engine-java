package com.github.guoyyit.graphnodeexecutionenginejava.engine.validation;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.EdgeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.WorkflowDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 定义校验器：在提交前整体校验定义。
 * 校验失败直接抛异常，不会先执行一部分再失败。
 */
public final class DefinitionValidator {

    private DefinitionValidator() {
    }

    /**
     * 校验定义，失败时抛出 {@link DefinitionValidationException}。
     *
     * @return 确定性的拓扑序（按节点 id 字典序打破平局），供调度器使用
     */
    public static List<String> validate(WorkflowDefinition definition) {
        if (definition == null
                || definition.id() == null || definition.id().isBlank()
                || definition.version() == null || definition.version().isBlank()) {
            throw new DefinitionValidationException(
                    ValidationFailureReason.MALFORMED, "definition id/version must not be blank");
        }

        Set<String> nodeIds = new HashSet<>();
        for (NodeDefinition node : definition.nodes()) {
            if (node.id() == null || node.id().isBlank()
                    || node.type() == null || node.type().isBlank()) {
                throw new DefinitionValidationException(
                        ValidationFailureReason.MALFORMED, "node id/type must not be blank");
            }
            if (!nodeIds.add(node.id())) {
                throw new DefinitionValidationException(
                        ValidationFailureReason.DUPLICATE_NODE, "duplicate node id: " + node.id());
            }
        }

        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        Map<String, Integer> indegree = new LinkedHashMap<>();
        for (String id : nodeIds) {
            outgoing.put(id, new ArrayList<>());
            indegree.put(id, 0);
        }
        for (EdgeDefinition edge : definition.edges()) {
            if (!nodeIds.contains(edge.from())) {
                throw new DefinitionValidationException(
                        ValidationFailureReason.UNKNOWN_NODE,
                        "edge references unknown node: " + edge.from());
            }
            if (!nodeIds.contains(edge.to())) {
                throw new DefinitionValidationException(
                        ValidationFailureReason.UNKNOWN_NODE,
                        "edge references unknown node: " + edge.to());
            }
            outgoing.get(edge.from()).add(edge.to());
            indegree.merge(edge.to(), 1, Integer::sum);
        }

        // Kahn 拓扑排序；同一时刻多个可调度节点按 id 字典序出队，保证确定性。
        Map<String, Integer> remaining = new HashMap<>(indegree);
        Deque<String> ready = new ArrayDeque<>();
        nodeIds.stream().sorted().filter(id -> remaining.get(id) == 0).forEach(ready::add);
        List<String> topoOrder = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.poll();
            topoOrder.add(id);
            List<String> next = outgoing.get(id).stream().sorted().toList();
            for (String to : next) {
                int left = remaining.merge(to, -1, Integer::sum);
                if (left == 0) {
                    ready.add(to);
                }
            }
        }
        if (topoOrder.size() != nodeIds.size()) {
            Set<String> inCycle = new HashSet<>(nodeIds);
            inCycle.removeAll(topoOrder);
            throw new DefinitionValidationException(
                    ValidationFailureReason.CYCLE_DETECTED,
                    "cycle detected among nodes: " + inCycle.stream().sorted().toList());
        }
        return topoOrder;
    }
}

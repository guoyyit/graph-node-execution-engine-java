package com.github.guoyyit.graphnodeexecutionenginejava.engine.exec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 执行器注册表，按 type 查找执行器。
 */
public class NodeExecutorRegistry {

    private final Map<String, NodeExecutor> executors = new LinkedHashMap<>();

    public NodeExecutorRegistry() {
    }

    public NodeExecutorRegistry(List<NodeExecutor> builtins) {
        if (builtins != null) {
            for (NodeExecutor executor : builtins) {
                register(executor);
            }
        }
    }

    public void register(NodeExecutor executor) {
        executors.put(executor.type(), executor);
    }

    public NodeExecutor require(String type) {
        NodeExecutor executor = executors.get(type);
        if (executor == null) {
            throw new IllegalArgumentException("no executor registered for type: " + type);
        }
        return executor;
    }

    public boolean supports(String type) {
        return executors.containsKey(type);
    }
}

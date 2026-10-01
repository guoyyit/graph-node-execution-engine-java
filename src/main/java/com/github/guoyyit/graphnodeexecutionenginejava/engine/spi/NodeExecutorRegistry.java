package com.github.guoyyit.graphnodeexecutionenginejava.engine.spi;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 节点类型 -> 执行器 的注册表。 */
public class NodeExecutorRegistry {

    private final Map<String, NodeExecutor> executors = new ConcurrentHashMap<>();

    public void register(String type, NodeExecutor executor) {
        executors.put(type, executor);
    }

    public NodeExecutor require(String type) {
        NodeExecutor executor = executors.get(type);
        if (executor == null) {
            throw new IllegalStateException("no executor registered for node type: " + type);
        }
        return executor;
    }
}

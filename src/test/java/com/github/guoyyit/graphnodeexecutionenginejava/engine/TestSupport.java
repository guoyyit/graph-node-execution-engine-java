package com.github.guoyyit.graphnodeexecutionenginejava.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.core.WorkflowEngine;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.EdgeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.FailurePolicy;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.WorkflowDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.spi.NodeExecutorRegistry;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.store.DefinitionStore;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.store.FileRunStore;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 测试辅助：构造定义与引擎。 */
final class TestSupport {

    private TestSupport() {
    }

    static NodeDefinition node(String id) {
        return new NodeDefinition(id, "test", Map.of(), 0, 0, 0, FailurePolicy.ABORT_RUN);
    }

    static NodeDefinition node(String id, long timeoutMs, int maxRetries,
                               long backoffMs, FailurePolicy policy) {
        return new NodeDefinition(id, "test", Map.of(), timeoutMs, maxRetries, backoffMs, policy);
    }

    static EdgeDefinition edge(String from, String to) {
        return new EdgeDefinition(from, to, null);
    }

    static EdgeDefinition edge(String from, String to, String condition) {
        return new EdgeDefinition(from, to, condition);
    }

    static WorkflowDefinition definition(String id, String version,
                                         List<NodeDefinition> nodes, EdgeDefinition... edges) {
        return new WorkflowDefinition(id, version, nodes, Arrays.asList(edges));
    }

    static WorkflowEngine newEngine(Path dir, NodeExecutorRegistry registry, int concurrency) {
        ObjectMapper mapper = new ObjectMapper();
        return new WorkflowEngine(
                new DefinitionStore(dir.resolve("definitions"), mapper),
                new FileRunStore(dir.resolve("runs"), mapper),
                registry,
                concurrency);
    }
}

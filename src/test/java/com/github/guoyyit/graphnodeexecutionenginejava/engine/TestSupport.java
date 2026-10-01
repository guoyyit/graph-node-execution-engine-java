package com.github.guoyyit.graphnodeexecutionenginejava.engine;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.def.DefinitionService;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeExecutorRegistry;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors.DataExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors.EffectExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors.FlakyExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors.NoopExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors.SleepExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors.SpelExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.EdgeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.FailureStrategy;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.GraphDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.JoinMode;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunState;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.runtime.ExecutionEngine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 测试辅助：构造本地引擎与定义。
 */
final class TestSupport {

    private TestSupport() {
    }

    static Path tempDir() throws Exception {
        return Files.createTempDirectory("graph-engine-test-");
    }

    static ExecutionEngine engine(Path dataDir) {
        return engine(dataDir, 4, FailureStrategy.ABORT);
    }

    static ExecutionEngine engine(Path dataDir, int defaultConcurrency, FailureStrategy strategy) {
        DefinitionService definitions = new DefinitionService(dataDir);
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register(new NoopExecutor());
        registry.register(new DataExecutor());
        registry.register(new SpelExecutor());
        registry.register(new SleepExecutor());
        registry.register(new FlakyExecutor());
        registry.register(new EffectExecutor());
        return new ExecutionEngine(dataDir, definitions, registry,
                defaultConcurrency, strategy, 64);
    }

    static NodeDefinition node(String id, String type) {
        return node(id, type, Map.of());
    }

    static NodeDefinition node(String id, String type, Map<String, Object> config) {
        return new NodeDefinition(id, id, type, config, null, 0, null, JoinMode.ALL);
    }

    static NodeDefinition node(String id, String type, Map<String, Object> config,
                               Duration timeout, int retries, Duration backoff) {
        return new NodeDefinition(id, id, type, config, timeout, retries, backoff, JoinMode.ALL);
    }

    static NodeDefinition node(String id, String type, Map<String, Object> config, JoinMode join) {
        return new NodeDefinition(id, id, type, config, null, 0, null, join);
    }

    static EdgeDefinition edge(String source, String target) {
        return new EdgeDefinition(source, target, null);
    }

    static EdgeDefinition edge(String source, String target, String condition) {
        return new EdgeDefinition(source, target, condition);
    }

    static GraphDefinition definition(String name, NodeDefinition... nodes) {
        return new GraphDefinition(name, 1, List.of(nodes), List.of());
    }

    static GraphDefinition definition(String name, List<NodeDefinition> nodes, List<EdgeDefinition> edges) {
        return new GraphDefinition(name, 1, nodes, edges);
    }

    static void awaitTerminal(ExecutionEngine engine, String runId, Duration timeout)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            RunStatus status = engine.getRun(runId).getStatus();
            if (status.isTerminal()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("run " + runId + " did not reach terminal within " + timeout);
    }

    static void awaitStatus(ExecutionEngine engine, String runId, RunStatus expected, Duration timeout)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeout.toMillis();
        while (System.currentTimeMillis() < deadline) {
            if (engine.getRun(runId).getStatus() == expected) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError("run " + runId + " did not reach " + expected + " within " + timeout);
    }

    /**
     * 注册定义并启动运行。
     */
    static RunState start(ExecutionEngine engine, GraphDefinition definition, int concurrency) {
        return start(engine, definition, concurrency, null);
    }

    static RunState start(ExecutionEngine engine, GraphDefinition definition, int concurrency,
                         FailureStrategy strategy) {
        GraphDefinition registered = engine.registerDefinition(definition);
        return engine.startRun(registered.name(), registered.version(), concurrency, strategy);
    }
}

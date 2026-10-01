package com.github.guoyyit.graphnodeexecutionenginejava.engine;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.core.WorkflowEngine;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunSnapshot;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.WorkflowDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.spi.NodeExecutorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConcurrencyTest {

    @TempDir
    Path dir;

    /** 4 个相互独立的节点在并发度 4 下应并行推进（同时处于执行中）。 */
    @Test
    void independentNodesRunInParallel() throws Exception {
        int parallelism = 4;
        CountDownLatch allStarted = new CountDownLatch(parallelism);
        CountDownLatch release = new CountDownLatch(1);

        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register("test", ctx -> {
            allStarted.countDown();
            // 若不能并行，此处将永远等不到全部启动
            assertTrue(allStarted.await(5, TimeUnit.SECONDS),
                    "nodes did not start concurrently");
            release.await(5, TimeUnit.SECONDS);
            return Map.of("node", ctx.nodeId());
        });

        List<NodeDefinition> nodes = new ArrayList<>();
        for (int i = 0; i < parallelism; i++) {
            nodes.add(TestSupport.node("n" + i));
        }
        try (WorkflowEngine engine = TestSupport.newEngine(dir, registry, parallelism)) {
            engine.registerDefinition(TestSupport.definition("par", "v1", nodes));
            String runId = engine.submit("par", "v1", Map.of());
            release.countDown();
            engine.await(runId);

            RunSnapshot run = engine.getRun(runId).orElseThrow();
            assertEquals(RunStatus.SUCCESS, run.getStatus());
            run.getNodeResults().values().forEach(
                    r -> assertEquals(NodeStatus.SUCCESS, r.getStatus()));
        }
    }

    /** 并发度为 1 时任意时刻最多一个节点在执行。 */
    @Test
    void concurrencyLimitIsRespected() throws Exception {
        AtomicInteger inFlightNow = new AtomicInteger();
        AtomicInteger maxObserved = new AtomicInteger();

        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register("test", ctx -> {
            int now = inFlightNow.incrementAndGet();
            maxObserved.accumulateAndGet(now, Math::max);
            Thread.sleep(30);
            inFlightNow.decrementAndGet();
            return Map.of();
        });

        List<NodeDefinition> nodes = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            nodes.add(TestSupport.node("n" + i));
        }
        try (WorkflowEngine engine = TestSupport.newEngine(dir, registry, 1)) {
            engine.registerDefinition(TestSupport.definition("seq", "v1", nodes));
            String runId = engine.submit("seq", "v1", Map.of());
            engine.await(runId);

            assertEquals(RunStatus.SUCCESS, engine.getRun(runId).orElseThrow().getStatus());
            assertEquals(1, maxObserved.get());
        }
    }

    /** 同样定义与并发度下，重复执行的最终结果与每个节点可见结果一致。 */
    @Test
    void resultsAreDeterministicAcrossRuns() throws Exception {
        Map<String, Integer> sharedCounter = new ConcurrentHashMap<>();
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register("test", ctx -> {
            // 每个节点输出确定值；下游读取上游输出
            if (ctx.upstreamOutputs().isEmpty()) {
                return Map.of("origin", ctx.nodeId());
            }
            List<String> keys = new ArrayList<>(ctx.upstreamOutputs().keySet());
            return Map.of("from", keys);
        });

        List<NodeDefinition> nodes = List.of(
                TestSupport.node("a"), TestSupport.node("b"), TestSupport.node("c"));
        WorkflowDefinition def = TestSupport.definition("det", "v1", nodes,
                TestSupport.edge("a", "c"), TestSupport.edge("b", "c"));

        Map<String, Object> firstOutputs = null;
        for (int i = 0; i < 5; i++) {
            try (WorkflowEngine engine = TestSupport.newEngine(dir.resolve("r" + i), registry, 3)) {
                engine.registerDefinition(def);
                String runId = engine.submit("det", "v1", Map.of());
                engine.await(runId);
                RunSnapshot run = engine.getRun(runId).orElseThrow();
                assertEquals(RunStatus.SUCCESS, run.getStatus());
                Map<String, Object> outputs = new ConcurrentHashMap<>();
                run.getNodeResults().forEach((k, v) -> outputs.put(k, v.getOutput()));
                if (firstOutputs == null) {
                    firstOutputs = outputs;
                } else {
                    assertEquals(firstOutputs, outputs);
                }
            }
        }
    }
}

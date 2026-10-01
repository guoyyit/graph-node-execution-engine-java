package com.github.guoyyit.graphnodeexecutionenginejava.engine;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.core.WorkflowEngine;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.FailurePolicy;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunSnapshot;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.WorkflowDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.spi.NodeExecutorRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeoutRetryTest {

    @TempDir
    Path dir;

    /** 前两次失败、第三次成功：重试后节点成功，尝试次数为 3。 */
    @Test
    void retriesUntilSuccess() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register("test", ctx -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("transient");
            }
            return Map.of("ok", true);
        });

        NodeDefinition flaky = TestSupport.node("flaky", 0, 3, 10, FailurePolicy.ABORT_RUN);
        try (WorkflowEngine engine = TestSupport.newEngine(dir, registry, 2)) {
            engine.registerDefinition(TestSupport.definition("retry", "v1", List.of(flaky)));
            String runId = engine.submit("retry", "v1", Map.of());
            engine.await(runId);

            RunSnapshot run = engine.getRun(runId).orElseThrow();
            assertEquals(RunStatus.SUCCESS, run.getStatus());
            assertEquals(NodeStatus.SUCCESS, run.getNodeResults().get("flaky").getStatus());
            assertEquals(3, run.getNodeResults().get("flaky").getAttempts());
        }
    }

    /** 重试之间应有退避：相邻尝试的开始间隔不小于退避基数。 */
    @Test
    void backoffBetweenRetries() throws Exception {
        CopyOnWriteArrayList<Long> attemptTimes = new CopyOnWriteArrayList<>();
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register("test", ctx -> {
            attemptTimes.add(System.currentTimeMillis());
            throw new RuntimeException("always fails");
        });

        long backoff = 150;
        NodeDefinition node = TestSupport.node("n", 0, 2, backoff, FailurePolicy.CONTINUE);
        try (WorkflowEngine engine = TestSupport.newEngine(dir, registry, 2)) {
            engine.registerDefinition(TestSupport.definition("backoff", "v1", List.of(node)));
            String runId = engine.submit("backoff", "v1", Map.of());
            engine.await(runId);

            assertEquals(3, attemptTimes.size());
            assertTrue(attemptTimes.get(1) - attemptTimes.get(0) >= backoff - 20,
                    "first backoff too short: " + (attemptTimes.get(1) - attemptTimes.get(0)));
            assertTrue(attemptTimes.get(2) - attemptTimes.get(1) >= backoff * 2 - 20,
                    "second backoff too short: " + (attemptTimes.get(2) - attemptTimes.get(1)));
        }
    }

    /** 单次尝试超时计入失败并触发重试，耗尽后按 ABORT_RUN 中止整个运行。 */
    @Test
    void timeoutExhaustsRetriesAndAbortsRun() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register("test", ctx -> {
            calls.incrementAndGet();
            Thread.sleep(10_000); // 会被超时中断
            return Map.of();
        });

        NodeDefinition slow = TestSupport.node("slow", 100, 1, 10, FailurePolicy.ABORT_RUN);
        NodeDefinition after = TestSupport.node("after");
        WorkflowDefinition def = TestSupport.definition("timeout", "v1",
                List.of(slow, after), TestSupport.edge("slow", "after"));

        try (WorkflowEngine engine = TestSupport.newEngine(dir, registry, 2)) {
            engine.registerDefinition(def);
            long start = System.currentTimeMillis();
            String runId = engine.submit("timeout", "v1", Map.of());
            engine.await(runId);
            long elapsed = System.currentTimeMillis() - start;

            RunSnapshot run = engine.getRun(runId).orElseThrow();
            assertEquals(RunStatus.FAILED, run.getStatus());
            assertEquals(NodeStatus.FAILED, run.getNodeResults().get("slow").getStatus());
            assertEquals(2, run.getNodeResults().get("slow").getAttempts());
            assertNotNull(run.getNodeResults().get("slow").getError());
            assertTrue(run.getNodeResults().get("slow").getError().contains("timed out"));
            // 下游不得被调度
            assertEquals(NodeStatus.CANCELLED, run.getNodeResults().get("after").getStatus());
            // 两次尝试各 100ms 超时 + 退避，远小于 10s
            assertTrue(elapsed < 5_000, "took too long: " + elapsed);
            assertNotNull(run.getFailureReason());
        }
    }

    /** 失败策略 CONTINUE：节点失败但运行继续，独立分支照常完成。 */
    @Test
    void continuePolicyKeepsRunGoing() throws Exception {
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register("test", ctx -> {
            if (ctx.nodeId().equals("bad")) {
                throw new RuntimeException("boom");
            }
            return Map.of("node", ctx.nodeId());
        });

        NodeDefinition bad = TestSupport.node("bad", 0, 0, 0, FailurePolicy.CONTINUE);
        NodeDefinition independent = TestSupport.node("independent");
        NodeDefinition downstream = TestSupport.node("downstream");
        WorkflowDefinition def = TestSupport.definition("continue", "v1",
                List.of(bad, independent, downstream),
                TestSupport.edge("bad", "downstream"));

        try (WorkflowEngine engine = TestSupport.newEngine(dir, registry, 2)) {
            engine.registerDefinition(def);
            String runId = engine.submit("continue", "v1", Map.of());
            engine.await(runId);

            RunSnapshot run = engine.getRun(runId).orElseThrow();
            // 运行继续完成，但整体标记为 FAILED 且原因可查
            assertEquals(RunStatus.FAILED, run.getStatus());
            assertNotNull(run.getFailureReason());
            assertEquals(NodeStatus.FAILED, run.getNodeResults().get("bad").getStatus());
            // 失败节点的下游被跳过，独立分支成功
            assertEquals(NodeStatus.SKIPPED, run.getNodeResults().get("downstream").getStatus());
            assertEquals(NodeStatus.SUCCESS, run.getNodeResults().get("independent").getStatus());
        }
    }

    /** 已完成节点的结果在失败后仍可查询。 */
    @Test
    void completedResultsRemainQueryableAfterFailure() throws Exception {
        Map<String, String> outputs = new ConcurrentHashMap<>();
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register("test", ctx -> {
            if (ctx.nodeId().equals("bad")) {
                throw new RuntimeException("boom");
            }
            return Map.of("done", ctx.nodeId());
        });

        NodeDefinition ok = TestSupport.node("ok");
        NodeDefinition bad = TestSupport.node("bad", 0, 1, 10, FailurePolicy.CONTINUE);
        try (WorkflowEngine engine = TestSupport.newEngine(dir, registry, 2)) {
            engine.registerDefinition(TestSupport.definition("query", "v1", List.of(ok, bad)));
            String runId = engine.submit("query", "v1", Map.of());
            engine.await(runId);

            RunSnapshot run = engine.getRun(runId).orElseThrow();
            assertEquals(NodeStatus.SUCCESS, run.getNodeResults().get("ok").getStatus());
            assertEquals("ok", run.getNodeResults().get("ok").getOutput().get("done"));
            assertEquals(NodeStatus.FAILED, run.getNodeResults().get("bad").getStatus());
            assertTrue(run.getNodeResults().get("bad").getError().contains("boom"));
        }
    }
}

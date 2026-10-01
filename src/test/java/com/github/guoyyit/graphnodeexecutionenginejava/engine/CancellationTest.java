package com.github.guoyyit.graphnodeexecutionenginejava.engine;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.core.WorkflowEngine;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CancellationTest {

    @TempDir
    Path dir;

    /** 取消后不再调度新节点，正在执行的节点被标记，最终状态自洽。 */
    @Test
    void cancelStopsSchedulingAndMarksRunningNodes() throws Exception {
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch neverRelease = new CountDownLatch(1);
        AtomicInteger executed = new AtomicInteger();

        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register("test", ctx -> {
            executed.incrementAndGet();
            firstStarted.countDown();
            // 响应中断：被取消时尽快退出
            try {
                neverRelease.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            }
            return Map.of();
        });

        // 链式：a -> b -> c，取消时只有 a 在执行
        WorkflowDefinition def = TestSupport.definition("cancel", "v1",
                List.of(TestSupport.node("a"), TestSupport.node("b"), TestSupport.node("c")),
                TestSupport.edge("a", "b"), TestSupport.edge("b", "c"));

        try (WorkflowEngine engine = TestSupport.newEngine(dir, registry, 2)) {
            engine.registerDefinition(def);
            String runId = engine.submit("cancel", "v1", Map.of());
            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

            engine.cancel(runId);
            engine.await(runId);

            RunSnapshot run = engine.getRun(runId).orElseThrow();
            assertEquals(RunStatus.CANCELLED, run.getStatus());
            assertEquals(NodeStatus.CANCELLED, run.getNodeResults().get("a").getStatus());
            // 未调度的新节点不得执行
            assertEquals(NodeStatus.CANCELLED, run.getNodeResults().get("b").getStatus());
            assertEquals(NodeStatus.CANCELLED, run.getNodeResults().get("c").getStatus());
            assertEquals(1, executed.get(), "no new node should be scheduled after cancel");
        }
    }

    /** 重复取消幂等：多次取消后状态一致且不自相矛盾。 */
    @Test
    void repeatedCancelIsIdempotent() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register("test", ctx -> {
            started.countDown();
            Thread.sleep(30_000);
            return Map.of();
        });

        try (WorkflowEngine engine = TestSupport.newEngine(dir, registry, 2)) {
            engine.registerDefinition(TestSupport.definition("cancel2", "v1",
                    List.of(TestSupport.node("a"))));
            String runId = engine.submit("cancel2", "v1", Map.of());
            assertTrue(started.await(5, TimeUnit.SECONDS));

            engine.cancel(runId);
            engine.cancel(runId);
            engine.cancel(runId);
            engine.await(runId);
            // 终态后再取消也不应改变结果
            engine.cancel(runId);

            RunSnapshot run = engine.getRun(runId).orElseThrow();
            assertEquals(RunStatus.CANCELLED, run.getStatus());
            assertTrue(run.isCancelRequested());
        }
    }

    /** 已完成节点在取消后保持 SUCCESS，不会被改写。 */
    @Test
    void completedNodesKeepResultsAfterCancel() throws Exception {
        CountDownLatch slowStarted = new CountDownLatch(1);
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register("test", ctx -> {
            if (ctx.nodeId().equals("slow")) {
                slowStarted.countDown();
                Thread.sleep(30_000);
            }
            return Map.of("done", ctx.nodeId());
        });

        WorkflowDefinition def = TestSupport.definition("cancel3", "v1",
                List.of(TestSupport.node("fast"), TestSupport.node("slow")));

        try (WorkflowEngine engine = TestSupport.newEngine(dir, registry, 2)) {
            engine.registerDefinition(def);
            String runId = engine.submit("cancel3", "v1", Map.of());
            assertTrue(slowStarted.await(5, TimeUnit.SECONDS));
            // 等 fast 完成
            waitFor(() -> engine.getRun(runId).orElseThrow()
                    .getNodeResults().get("fast").getStatus() == NodeStatus.SUCCESS);

            engine.cancel(runId);
            engine.await(runId);

            RunSnapshot run = engine.getRun(runId).orElseThrow();
            assertEquals(RunStatus.CANCELLED, run.getStatus());
            assertEquals(NodeStatus.SUCCESS, run.getNodeResults().get("fast").getStatus());
            assertEquals("fast", run.getNodeResults().get("fast").getOutput().get("done"));
            assertEquals(NodeStatus.CANCELLED, run.getNodeResults().get("slow").getStatus());
        }
    }

    private static void waitFor(Check check) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5_000;
        while (!check.ok()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition not met in time");
            }
            Thread.sleep(20);
        }
    }

    @FunctionalInterface
    private interface Check {
        boolean ok();
    }
}

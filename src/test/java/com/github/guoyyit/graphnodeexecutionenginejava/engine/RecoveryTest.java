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

class RecoveryTest {

    @TempDir
    Path dir;

    /**
     * 模拟进程崩溃：a 已完成、b 处于 RUNNING 时“进程消失”（不关闭旧引擎）。
     * 新引擎从同一存储目录启动后：a 不得重跑，b 从 PENDING 重新执行并最终成功。
     */
    @Test
    void incompleteRunResumesAfterRestart() throws Exception {
        AtomicInteger aExecutions = new AtomicInteger();
        AtomicInteger bExecutions = new AtomicInteger();
        CountDownLatch bStarted = new CountDownLatch(1);
        CountDownLatch bBlockedForever = new CountDownLatch(1);

        // “崩溃前”的引擎：b 永远阻塞，模拟进程被杀时 b 仍在执行
        NodeExecutorRegistry registryBeforeCrash = new NodeExecutorRegistry();
        registryBeforeCrash.register("test", ctx -> {
            if (ctx.nodeId().equals("a")) {
                aExecutions.incrementAndGet();
                return Map.of("from", "a");
            }
            bExecutions.incrementAndGet();
            bStarted.countDown();
            bBlockedForever.await(); // 永不返回，模拟崩溃时仍在执行
            return Map.of();
        });

        WorkflowDefinition def = TestSupport.definition("rec", "v1",
                List.of(TestSupport.node("a"), TestSupport.node("b")),
                TestSupport.edge("a", "b"));

        WorkflowEngine crashed = TestSupport.newEngine(dir, registryBeforeCrash, 2);
        crashed.registerDefinition(def);
        String runId = crashed.submit("rec", "v1", Map.of());
        assertTrue(bStarted.await(5, TimeUnit.SECONDS));
        // 等 a 的结果落盘
        waitFor(() -> crashed.getRun(runId).orElseThrow()
                .getNodeResults().get("a").getStatus() == NodeStatus.SUCCESS);
        // 不关闭 crashed，直接“重启”：新引擎从同一目录恢复

        NodeExecutorRegistry registryAfterRestart = new NodeExecutorRegistry();
        registryAfterRestart.register("test", ctx -> {
            if (ctx.nodeId().equals("a")) {
                aExecutions.incrementAndGet();
                return Map.of("from", "a");
            }
            bExecutions.incrementAndGet();
            return Map.of("from", "b");
        });

        try (WorkflowEngine recovered = TestSupport.newEngine(dir, registryAfterRestart, 2)) {
            recovered.await(runId);

            RunSnapshot run = recovered.getRun(runId).orElseThrow();
            assertEquals(RunStatus.SUCCESS, run.getStatus());
            assertEquals(NodeStatus.SUCCESS, run.getNodeResults().get("a").getStatus());
            assertEquals(NodeStatus.SUCCESS, run.getNodeResults().get("b").getStatus());
            assertEquals(Map.of("from", "a"), run.getNodeResults().get("a").getOutput());
            assertEquals(Map.of("from", "b"), run.getNodeResults().get("b").getOutput());
            // 已完成节点不得被再次执行
            assertEquals(1, aExecutions.get(), "completed node a must not re-execute");
            // b 崩溃前执行过一次（未完成），恢复后重新执行一次
            assertEquals(2, bExecutions.get());
        }
    }

    /** 已完成的运行在重启后保持终态，不会被重新调度。 */
    @Test
    void completedRunStaysCompletedAfterRestart() throws Exception {
        AtomicInteger executions = new AtomicInteger();
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register("test", ctx -> {
            executions.incrementAndGet();
            return Map.of("done", true);
        });

        String runId;
        try (WorkflowEngine engine = TestSupport.newEngine(dir, registry, 2)) {
            engine.registerDefinition(TestSupport.definition("done", "v1",
                    List.of(TestSupport.node("a"))));
            runId = engine.submit("done", "v1", Map.of());
            engine.await(runId);
            assertEquals(RunStatus.SUCCESS, engine.getRun(runId).orElseThrow().getStatus());
        }

        try (WorkflowEngine restarted = TestSupport.newEngine(dir, registry, 2)) {
            Thread.sleep(200); // 给恢复逻辑时间（不应有任何动作）
            RunSnapshot run = restarted.getRun(runId).orElseThrow();
            assertEquals(RunStatus.SUCCESS, run.getStatus());
            assertEquals(1, executions.get(), "completed run must not be rescheduled");
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

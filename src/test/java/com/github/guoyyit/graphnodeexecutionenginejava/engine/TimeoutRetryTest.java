package com.github.guoyyit.graphnodeexecutionenginejava.engine;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.FailureStrategy;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.GraphDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunState;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.runtime.ExecutionEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 超时与重试测试：节点级超时、有限重试与指数退避、重试耗尽后的失败策略。
 */
class TimeoutRetryTest {

    @TempDir
    Path dataDir;

    private ExecutionEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    @Test
    void nodeTimeoutFailsNode() throws InterruptedException {
        engine = TestSupport.engine(dataDir);
        GraphDefinition def = TestSupport.definition("timeout",
                List.of(TestSupport.node("slow", "sleep", Map.of("ms", 2000),
                        Duration.ofMillis(100), 0, Duration.ofMillis(50))),
                List.of());
        RunState run = TestSupport.start(engine, def, 1);
        TestSupport.awaitTerminal(engine, run.getRunId(), Duration.ofSeconds(10));

        RunState result = engine.getRun(run.getRunId());
        assertEquals(RunStatus.FAILED, result.getStatus());
        assertEquals(NodeStatus.FAILED, result.nodeState("slow").getStatus());
        assertEquals(1, result.nodeState("slow").getAttempts());
    }

    @Test
    void retriesWithBackoffThenSucceeds() throws InterruptedException {
        engine = TestSupport.engine(dataDir);
        // flaky 前 2 次失败，最多重试 3 次（共 4 次尝试），退避 100ms
        GraphDefinition def = TestSupport.definition("retry",
                List.of(TestSupport.node("flaky", "flaky", Map.of("failTimes", 2),
                        null, 3, Duration.ofMillis(100))),
                List.of());
        long start = System.currentTimeMillis();
        RunState run = TestSupport.start(engine, def, 1);
        TestSupport.awaitTerminal(engine, run.getRunId(), Duration.ofSeconds(10));
        long elapsed = System.currentTimeMillis() - start;

        RunState result = engine.getRun(run.getRunId());
        assertEquals(RunStatus.SUCCEEDED, result.getStatus());
        assertEquals(NodeStatus.SUCCEEDED, result.nodeState("flaky").getStatus());
        // 第 3 次尝试成功
        assertEquals(3, result.nodeState("flaky").getAttempts());
        // 退避：100 + 200 = 300ms 左右
        assertTrue(elapsed >= 250, "expected backoff delay, took " + elapsed + "ms");
    }

    @Test
    void retryExhaustedWithAbortStrategy() throws InterruptedException {
        engine = TestSupport.engine(dataDir, 2, FailureStrategy.ABORT);
        GraphDefinition def = TestSupport.definition("abort",
                List.of(TestSupport.node("flaky", "flaky", Map.of("failTimes", 10),
                        null, 1, Duration.ofMillis(20)),
                        TestSupport.node("down", "noop"),
                        TestSupport.node("indep", "noop")),
                List.of(TestSupport.edge("flaky", "down")));
        RunState run = TestSupport.start(engine, def, 2);
        TestSupport.awaitTerminal(engine, run.getRunId(), Duration.ofSeconds(10));

        RunState result = engine.getRun(run.getRunId());
        assertEquals(RunStatus.FAILED, result.getStatus());
        assertEquals(NodeStatus.FAILED, result.nodeState("flaky").getStatus());
        // ABORT：下游与未完成节点被标记为 CANCELLED
        assertEquals(NodeStatus.CANCELLED, result.nodeState("down").getStatus());
        assertTrue(result.getError().contains("flaky"));
    }

    @Test
    void retryExhaustedWithContinueStrategySkipsDownstreamButKeepsIndependent() throws InterruptedException {
        engine = TestSupport.engine(dataDir, 2, FailureStrategy.CONTINUE);
        GraphDefinition def = TestSupport.definition("continue",
                List.of(TestSupport.node("flaky", "flaky", Map.of("failTimes", 10),
                        null, 1, Duration.ofMillis(20)),
                        TestSupport.node("down", "noop"),
                        TestSupport.node("indep", "noop")),
                List.of(TestSupport.edge("flaky", "down")));
        RunState run = TestSupport.start(engine, def, 2);
        TestSupport.awaitTerminal(engine, run.getRunId(), Duration.ofSeconds(10));

        RunState result = engine.getRun(run.getRunId());
        // 存在失败节点，运行整体为 FAILED
        assertEquals(RunStatus.FAILED, result.getStatus());
        assertEquals(NodeStatus.FAILED, result.nodeState("flaky").getStatus());
        // CONTINUE：失败节点的下游被跳过
        assertEquals(NodeStatus.SKIPPED, result.nodeState("down").getStatus());
        // 独立分支不受影响，正常成功
        assertEquals(NodeStatus.SUCCEEDED, result.nodeState("indep").getStatus());
    }
}

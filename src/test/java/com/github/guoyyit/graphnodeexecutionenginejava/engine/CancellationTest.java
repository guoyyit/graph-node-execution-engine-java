package com.github.guoyyit.graphnodeexecutionenginejava.engine;

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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 取消测试：取消后不再调度新节点、运行中节点停止、重复取消幂等、最终状态自洽。
 */
class CancellationTest {

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
    void cancelStopsRunningNodesAndNeverSchedulesNewOnes() throws InterruptedException {
        engine = TestSupport.engine(dataDir);
        // start -> {a,b,c} -> end，每个 sleep 5s
        GraphDefinition def = TestSupport.definition("cancel",
                List.of(TestSupport.node("start", "noop"),
                        TestSupport.node("a", "sleep", Map.of("ms", 5000)),
                        TestSupport.node("b", "sleep", Map.of("ms", 5000)),
                        TestSupport.node("c", "sleep", Map.of("ms", 5000)),
                        TestSupport.node("end", "noop")),
                List.of(TestSupport.edge("start", "a"), TestSupport.edge("start", "b"),
                        TestSupport.edge("start", "c"),
                        TestSupport.edge("a", "end"), TestSupport.edge("b", "end"),
                        TestSupport.edge("c", "end")));
        RunState run = TestSupport.start(engine, def, 3);
        // 等待 a/b/c 进入 RUNNING
        TestSupport.awaitStatus(engine, run.getRunId(), RunStatus.RUNNING, Duration.ofSeconds(5));
        long deadline = System.currentTimeMillis() + 5000;
        while (engine.getRun(run.getRunId()).runningNodeCount() < 3
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(3, engine.getRun(run.getRunId()).runningNodeCount());

        // 执行取消
        RunState cancelled = engine.cancel(run.getRunId());
        assertEquals(RunStatus.CANCELLED, cancelled.getStatus());

        // 等待任务线程响应中断
        TestSupport.awaitTerminal(engine, run.getRunId(), Duration.ofSeconds(5));

        RunState result = engine.getRun(run.getRunId());
        assertEquals(RunStatus.CANCELLED, result.getStatus());
        // 运行中的节点被标记为 CANCELLED
        assertEquals(NodeStatus.CANCELLED, result.nodeState("a").getStatus());
        assertEquals(NodeStatus.CANCELLED, result.nodeState("b").getStatus());
        assertEquals(NodeStatus.CANCELLED, result.nodeState("c").getStatus());
        // 尚未调度的节点不得再被调度
        assertEquals(NodeStatus.CANCELLED, result.nodeState("end").getStatus());
        // 不存在 PENDING 或 RUNNING 的节点，状态自洽
        long nonTerminal = result.getNodeStates().values().stream()
                .filter(ns -> !ns.isTerminal()).count();
        assertEquals(0, nonTerminal);
    }

    @Test
    void cancelIsIdempotent() throws InterruptedException {
        engine = TestSupport.engine(dataDir);
        GraphDefinition def = TestSupport.definition("cancel-idem",
                List.of(TestSupport.node("a", "sleep", Map.of("ms", 5000))),
                List.of());
        RunState run = TestSupport.start(engine, def, 1);
        TestSupport.awaitStatus(engine, run.getRunId(), RunStatus.RUNNING, Duration.ofSeconds(5));

        RunState first = engine.cancel(run.getRunId());
        RunState second = engine.cancel(run.getRunId());
        assertEquals(first.getStatus(), second.getStatus());
        assertEquals(first.getFinishedAt(), second.getFinishedAt());
        assertEquals(RunStatus.CANCELLED, second.getStatus());
    }

    @Test
    void cancelOnFinishedRunIsNoop() throws InterruptedException {
        engine = TestSupport.engine(dataDir);
        GraphDefinition def = TestSupport.definition("cancel-noop",
                List.of(TestSupport.node("a", "noop")),
                List.of());
        RunState run = TestSupport.start(engine, def, 1);
        TestSupport.awaitTerminal(engine, run.getRunId(), Duration.ofSeconds(5));
        RunState finished = engine.getRun(run.getRunId());
        assertEquals(RunStatus.SUCCEEDED, finished.getStatus());
        // 对已结束的运行取消应无副作用
        RunState cancelled = engine.cancel(run.getRunId());
        assertEquals(RunStatus.SUCCEEDED, cancelled.getStatus());
        assertEquals(finished.getFinishedAt(), cancelled.getFinishedAt());
    }
}

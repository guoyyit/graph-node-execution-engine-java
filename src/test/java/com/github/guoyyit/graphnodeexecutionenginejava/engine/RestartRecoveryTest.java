package com.github.guoyyit.graphnodeexecutionenginejava.engine;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.GraphDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunState;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.runtime.ExecutionEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 重启恢复测试：进程崩溃后从已提交位置继续，已完成节点不重复执行、无重复副作用。
 */
class RestartRecoveryTest {

    @TempDir
    Path dataDir;

    @Test
    void resumesFromCommittedPositionWithoutDuplicateEffects() throws Exception {
        Path effectFile = dataDir.resolve("effects.log");

        // 第一个引擎实例：a(effect) 快速完成，b(sleep) 仍在运行时崩溃
        ExecutionEngine first = TestSupport.engine(dataDir);
        GraphDefinition def = TestSupport.definition("recover",
                List.of(TestSupport.node("a", "effect", Map.of("path", effectFile.toString())),
                        TestSupport.node("b", "sleep", Map.of("ms", 3000)),
                        TestSupport.node("c", "noop")),
                List.of(TestSupport.edge("a", "c")));
        RunState run = TestSupport.start(first, def, 2);
        // 等待 a 完成、b 进入 RUNNING
        TestSupport.awaitStatus(first, run.getRunId(), RunStatus.RUNNING, Duration.ofSeconds(5));
        long deadline = System.currentTimeMillis() + 5000;
        while (first.getRun(run.getRunId()).nodeState("a").getStatus() != NodeStatus.SUCCEEDED
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(NodeStatus.SUCCEEDED, first.getRun(run.getRunId()).nodeState("a").getStatus());

        // 模拟进程崩溃：关闭线程池并中断调度线程，不做终结处理
        first.shutdown();
        Thread.sleep(200);

        // 崩溃后磁盘上的状态：a 已提交，b 仍在 RUNNING（未提交）
        RunState crashed = new com.github.guoyyit.graphnodeexecutionenginejava.engine.runtime.RunRepository(dataDir)
                .load(run.getRunId());
        assertEquals(NodeStatus.SUCCEEDED, crashed.nodeState("a").getStatus());
        assertEquals(NodeStatus.RUNNING, crashed.nodeState("b").getStatus());

        // 第二个引擎实例：恢复
        ExecutionEngine second = TestSupport.engine(dataDir);
        second.resume(run.getRunId());
        TestSupport.awaitTerminal(second, run.getRunId(), Duration.ofSeconds(10));

        RunState recovered = second.getRun(run.getRunId());
        assertEquals(RunStatus.SUCCEEDED, recovered.getStatus());
        assertEquals(NodeStatus.SUCCEEDED, recovered.nodeState("a").getStatus());
        assertEquals(NodeStatus.SUCCEEDED, recovered.nodeState("b").getStatus());
        assertEquals(NodeStatus.SUCCEEDED, recovered.nodeState("c").getStatus());

        // a 只执行过一次，副作用文件只有一行（恢复后没有重复效果）
        List<String> lines = Files.readAllLines(effectFile);
        assertEquals(1, lines.size());

        second.shutdown();
    }

    @Test
    void resumeAllOnStartupResumesInterruptedRuns() throws Exception {
        ExecutionEngine first = TestSupport.engine(dataDir);
        GraphDefinition def = TestSupport.definition("auto-recover",
                List.of(TestSupport.node("a", "sleep", Map.of("ms", 2000))),
                List.of());
        RunState run = TestSupport.start(first, def, 1);
        TestSupport.awaitStatus(first, run.getRunId(), RunStatus.RUNNING, Duration.ofSeconds(5));
        first.shutdown();
        Thread.sleep(200);

        // 新引擎启动时自动恢复
        ExecutionEngine second = TestSupport.engine(dataDir);
        second.resumeAllOnStartup();
        TestSupport.awaitTerminal(second, run.getRunId(), Duration.ofSeconds(10));

        assertEquals(RunStatus.SUCCEEDED, second.getRun(run.getRunId()).getStatus());
        second.shutdown();
    }

    @Test
    void terminalRunIsNotResumed() throws Exception {
        ExecutionEngine engine = TestSupport.engine(dataDir);
        GraphDefinition def = TestSupport.definition("terminal",
                List.of(TestSupport.node("a", "noop")),
                List.of());
        RunState run = TestSupport.start(engine, def, 1);
        TestSupport.awaitTerminal(engine, run.getRunId(), Duration.ofSeconds(5));
        assertEquals(RunStatus.SUCCEEDED, engine.getRun(run.getRunId()).getStatus());

        // 恢复一个已结束的运行应无变化
        RunState resumed = engine.resume(run.getRunId());
        assertEquals(RunStatus.SUCCEEDED, resumed.getStatus());
        engine.shutdown();
    }
}

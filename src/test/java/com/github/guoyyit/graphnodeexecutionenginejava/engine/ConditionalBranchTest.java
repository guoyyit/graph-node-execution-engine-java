package com.github.guoyyit.graphnodeexecutionenginejava.engine;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.EdgeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.GraphDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.JoinMode;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeDefinition;
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

/**
 * 条件分支测试：条件不成立的分支不得被调度，下游标记跳过；
 * 汇聚节点按依赖语义执行。
 */
class ConditionalBranchTest {

    @TempDir
    Path dataDir;

    private ExecutionEngine engine;

    @AfterEach
    void tearDown() {
        if (engine != null) {
            engine.shutdown();
        }
    }

    private void await(RunState run) throws InterruptedException {
        TestSupport.awaitTerminal(engine, run.getRunId(), Duration.ofSeconds(10));
    }

    @Test
    void falseBranchIsSkippedAndTrueBranchRuns() throws InterruptedException {
        engine = TestSupport.engine(dataDir);
        GraphDefinition def = TestSupport.definition("cond",
                List.of(TestSupport.node("start", "data", Map.of("data", Map.of("flag", true))),
                        TestSupport.node("yes", "noop"),
                        TestSupport.node("no", "noop")),
                List.of(TestSupport.edge("start", "yes", "#output['flag'] == true"),
                        TestSupport.edge("start", "no", "#output['flag'] == false")));
        RunState run = TestSupport.start(engine, def, 2);
        await(run);

        RunState result = engine.getRun(run.getRunId());
        assertEquals(RunStatus.SUCCEEDED, result.getStatus());
        assertEquals(NodeStatus.SUCCEEDED, result.nodeState("yes").getStatus());
        assertEquals(NodeStatus.SKIPPED, result.nodeState("no").getStatus());
        // 被跳过的节点没有输出
        assertEquals(false, result.getOutputs().containsKey("no"));
    }

    @Test
    void falseBranchCascadesToDownstream() throws InterruptedException {
        engine = TestSupport.engine(dataDir);
        GraphDefinition def = TestSupport.definition("cascade",
                List.of(TestSupport.node("start", "data", Map.of("data", Map.of("flag", false))),
                        TestSupport.node("a", "noop"),
                        TestSupport.node("b", "noop")),
                List.of(TestSupport.edge("start", "a", "#output['flag'] == true"),
                        TestSupport.edge("a", "b")));
        RunState run = TestSupport.start(engine, def, 2);
        await(run);

        RunState result = engine.getRun(run.getRunId());
        assertEquals(RunStatus.SUCCEEDED, result.getStatus());
        assertEquals(NodeStatus.SKIPPED, result.nodeState("a").getStatus());
        assertEquals(NodeStatus.SKIPPED, result.nodeState("b").getStatus());
    }

    @Test
    void allJoinSkipsWhenOneBranchInactive() throws InterruptedException {
        engine = TestSupport.engine(dataDir);
        NodeDefinition join = TestSupport.node("join", "noop", Map.of(), JoinMode.ALL);
        GraphDefinition def = TestSupport.definition("all-join",
                List.of(TestSupport.node("a", "data", Map.of("data", Map.of("take", true))),
                        TestSupport.node("b", "data", Map.of("data", Map.of("take", false))),
                        join),
                List.of(TestSupport.edge("a", "join", "#output['take'] == true"),
                        TestSupport.edge("b", "join", "#output['take'] == true")));
        RunState run = TestSupport.start(engine, def, 2);
        await(run);

        RunState result = engine.getRun(run.getRunId());
        assertEquals(NodeStatus.SUCCEEDED, result.nodeState("a").getStatus());
        // b 节点本身执行成功，只是其到 join 的边条件不成立
        assertEquals(NodeStatus.SUCCEEDED, result.nodeState("b").getStatus());
        // ALL 汇聚：b 分支不成立，join 跳过
        assertEquals(NodeStatus.SKIPPED, result.nodeState("join").getStatus());
    }

    @Test
    void anyJoinRunsWhenAtLeastOneBranchActive() throws InterruptedException {
        engine = TestSupport.engine(dataDir);
        NodeDefinition join = TestSupport.node("join", "noop", Map.of(), JoinMode.ANY);
        GraphDefinition def = TestSupport.definition("any-join",
                List.of(TestSupport.node("a", "data", Map.of("data", Map.of("take", true))),
                        TestSupport.node("b", "data", Map.of("data", Map.of("take", false))),
                        join),
                List.of(TestSupport.edge("a", "join", "#output['take'] == true"),
                        TestSupport.edge("b", "join", "#output['take'] == true")));
        RunState run = TestSupport.start(engine, def, 2);
        await(run);

        RunState result = engine.getRun(run.getRunId());
        assertEquals(NodeStatus.SUCCEEDED, result.nodeState("a").getStatus());
        assertEquals(NodeStatus.SUCCEEDED, result.nodeState("b").getStatus());
        // ANY 汇聚：a 分支成立即执行
        assertEquals(NodeStatus.SUCCEEDED, result.nodeState("join").getStatus());
    }

    @Test
    void unconditionalEdgesAlwaysActive() throws InterruptedException {
        engine = TestSupport.engine(dataDir);
        GraphDefinition def = TestSupport.definition("unconditional",
                List.of(TestSupport.node("a", "noop"), TestSupport.node("b", "noop"),
                        TestSupport.node("c", "noop")),
                List.of(TestSupport.edge("a", "b"), TestSupport.edge("a", "c")));
        RunState run = TestSupport.start(engine, def, 2);
        await(run);

        RunState result = engine.getRun(run.getRunId());
        assertEquals(RunStatus.SUCCEEDED, result.getStatus());
        assertEquals(NodeStatus.SUCCEEDED, result.nodeState("b").getStatus());
        assertEquals(NodeStatus.SUCCEEDED, result.nodeState("c").getStatus());
    }
}

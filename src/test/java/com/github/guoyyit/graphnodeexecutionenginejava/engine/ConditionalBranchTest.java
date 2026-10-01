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

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConditionalBranchTest {

    @TempDir
    Path dir;

    /**
     * 结构：root -> (left 条件: flag==true) / (right 条件: flag==false)，left/right -> join。
     * flag=true 时：left 执行、right 跳过，join 在 left 成功后执行。
     */
    @Test
    void untakenBranchIsSkippedAndJoinRunsOnTakenBranch() throws Exception {
        WorkflowDefinition def = TestSupport.definition("cond", "v1",
                List.of(TestSupport.node("root"), TestSupport.node("left"),
                        TestSupport.node("right"), TestSupport.node("join")),
                TestSupport.edge("root", "left", "flag == true"),
                TestSupport.edge("root", "right", "flag == false"),
                TestSupport.edge("left", "join"),
                TestSupport.edge("right", "join"));

        try (WorkflowEngine engine = engineReturning(Map.of("flag", true))) {
            engine.registerDefinition(def);
            String runId = engine.submit("cond", "v1", Map.of());
            engine.await(runId);

            RunSnapshot run = engine.getRun(runId).orElseThrow();
            assertEquals(RunStatus.SUCCESS, run.getStatus());
            assertEquals(NodeStatus.SUCCESS, run.getNodeResults().get("root").getStatus());
            assertEquals(NodeStatus.SUCCESS, run.getNodeResults().get("left").getStatus());
            assertEquals(NodeStatus.SKIPPED, run.getNodeResults().get("right").getStatus());
            assertEquals(NodeStatus.SUCCESS, run.getNodeResults().get("join").getStatus());
        }
    }

    /** flag=false 时走 right 分支，left 被跳过。 */
    @Test
    void oppositeConditionTakesOtherBranch() throws Exception {
        WorkflowDefinition def = TestSupport.definition("cond2", "v1",
                List.of(TestSupport.node("root"), TestSupport.node("left"),
                        TestSupport.node("right"), TestSupport.node("join")),
                TestSupport.edge("root", "left", "flag == true"),
                TestSupport.edge("root", "right", "flag == false"),
                TestSupport.edge("left", "join"),
                TestSupport.edge("right", "join"));

        try (WorkflowEngine engine = engineReturning(Map.of("flag", false))) {
            engine.registerDefinition(def);
            String runId = engine.submit("cond2", "v1", Map.of());
            engine.await(runId);

            RunSnapshot run = engine.getRun(runId).orElseThrow();
            assertEquals(RunStatus.SUCCESS, run.getStatus());
            assertEquals(NodeStatus.SKIPPED, run.getNodeResults().get("left").getStatus());
            assertEquals(NodeStatus.SUCCESS, run.getNodeResults().get("right").getStatus());
            assertEquals(NodeStatus.SUCCESS, run.getNodeResults().get("join").getStatus());
        }
    }

    /** 两条入边条件都不成立时，汇聚节点被明确标记为跳过。 */
    @Test
    void joinIsSkippedWhenNoIncomingEdgeIsTaken() throws Exception {
        WorkflowDefinition def = TestSupport.definition("cond3", "v1",
                List.of(TestSupport.node("root"), TestSupport.node("join")),
                TestSupport.edge("root", "join", "flag == 'never'"));

        try (WorkflowEngine engine = engineReturning(Map.of("flag", "no"))) {
            engine.registerDefinition(def);
            String runId = engine.submit("cond3", "v1", Map.of());
            engine.await(runId);

            RunSnapshot run = engine.getRun(runId).orElseThrow();
            assertEquals(RunStatus.SUCCESS, run.getStatus());
            assertEquals(NodeStatus.SKIPPED, run.getNodeResults().get("join").getStatus());
        }
    }

    /** 下游节点能看到被采用分支上游的输出。 */
    @Test
    void downstreamSeesUpstreamOutput() throws Exception {
        WorkflowDefinition def = TestSupport.definition("cond4", "v1",
                List.of(TestSupport.node("root"), TestSupport.node("down")),
                TestSupport.edge("root", "down"));

        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register("test", ctx -> {
            if (ctx.nodeId().equals("root")) {
                return Map.of("value", 42);
            }
            Map<String, Object> up = ctx.upstreamOutputs().get("root");
            return Map.of("seen", up.get("value"));
        });

        try (WorkflowEngine engine = TestSupport.newEngine(dir, registry, 2)) {
            engine.registerDefinition(def);
            String runId = engine.submit("cond4", "v1", Map.of());
            engine.await(runId);

            RunSnapshot run = engine.getRun(runId).orElseThrow();
            assertEquals(42, run.getNodeResults().get("down").getOutput().get("seen"));
        }
    }

    private WorkflowEngine engineReturning(Map<String, Object> rootOutput) {
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register("test", ctx -> ctx.nodeId().equals("root") ? rootOutput : Map.of());
        return TestSupport.newEngine(dir, registry, 2);
    }
}

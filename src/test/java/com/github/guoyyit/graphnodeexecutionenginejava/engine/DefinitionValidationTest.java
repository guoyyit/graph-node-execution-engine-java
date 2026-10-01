package com.github.guoyyit.graphnodeexecutionenginejava.engine;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.core.WorkflowEngine;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.WorkflowDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.spi.NodeExecutorRegistry;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.validation.DefinitionValidationException;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.validation.ValidationFailureReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefinitionValidationTest {

    @TempDir
    Path dir;

    @Test
    void cycleIsRejectedWithDistinguishableReason() {
        try (WorkflowEngine engine = newEngine()) {
            WorkflowDefinition def = TestSupport.definition("cyclic", "v1",
                    List.of(TestSupport.node("a"), TestSupport.node("b"), TestSupport.node("c")),
                    TestSupport.edge("a", "b"), TestSupport.edge("b", "c"), TestSupport.edge("c", "a"));

            DefinitionValidationException ex = assertThrows(DefinitionValidationException.class,
                    () -> engine.registerDefinition(def));
            assertEquals(ValidationFailureReason.CYCLE_DETECTED, ex.getReason());
            // 整体拒绝：定义未落盘，无法提交运行
            assertTrue(engine.listRunIds().isEmpty());
            assertThrows(IllegalArgumentException.class,
                    () -> engine.submit("cyclic", "v1", Map.of()));
        }
    }

    @Test
    void edgeToUnknownNodeIsRejectedWithDistinguishableReason() {
        try (WorkflowEngine engine = newEngine()) {
            WorkflowDefinition def = TestSupport.definition("dangling", "v1",
                    List.of(TestSupport.node("a")),
                    TestSupport.edge("a", "ghost"));

            DefinitionValidationException ex = assertThrows(DefinitionValidationException.class,
                    () -> engine.registerDefinition(def));
            assertEquals(ValidationFailureReason.UNKNOWN_NODE, ex.getReason());
        }
    }

    @Test
    void duplicateNodeIdIsRejected() {
        try (WorkflowEngine engine = newEngine()) {
            WorkflowDefinition def = TestSupport.definition("dup", "v1",
                    List.of(TestSupport.node("a"), TestSupport.node("a")));

            DefinitionValidationException ex = assertThrows(DefinitionValidationException.class,
                    () -> engine.registerDefinition(def));
            assertEquals(ValidationFailureReason.DUPLICATE_NODE, ex.getReason());
        }
    }

    @Test
    void sameDefinitionIdCanCoexistInMultipleVersions() throws InterruptedException {
        try (WorkflowEngine engine = newEngine()) {
            engine.registerDefinition(TestSupport.definition("flow", "v1",
                    List.of(TestSupport.node("a"))));
            engine.registerDefinition(TestSupport.definition("flow", "v2",
                    List.of(TestSupport.node("a"), TestSupport.node("b")),
                    TestSupport.edge("a", "b")));

            String run1 = engine.submit("flow", "v1", Map.of());
            String run2 = engine.submit("flow", "v2", Map.of());
            engine.await(run1);
            engine.await(run2);

            assertEquals(1, engine.getRun(run1).orElseThrow().getNodeResults().size());
            assertEquals(2, engine.getRun(run2).orElseThrow().getNodeResults().size());
        }
    }

    private WorkflowEngine newEngine() {
        return TestSupport.newEngine(dir, noopRegistry(), 2);
    }

    static NodeExecutorRegistry noopRegistry() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register("test", ctx -> Map.of("node", ctx.nodeId()));
        return registry;
    }
}

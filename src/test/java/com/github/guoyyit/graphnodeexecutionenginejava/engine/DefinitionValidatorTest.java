package com.github.guoyyit.graphnodeexecutionenginejava.engine;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.def.DefinitionException;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.def.DefinitionService;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.EdgeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.GraphDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定义校验测试：环检测、悬空连线、重复节点 id，且失败时整体拒绝。
 */
class DefinitionValidatorTest {

    @TempDir
    Path dataDir;

    private DefinitionService definitions() {
        return new DefinitionService(dataDir);
    }

    @Test
    void validDefinitionIsAccepted() {
        GraphDefinition def = TestSupport.definition("ok",
                List.of(TestSupport.node("a", "noop"), TestSupport.node("b", "noop")),
                List.of(TestSupport.edge("a", "b")));
        GraphDefinition registered = definitions().register(def);
        assertEquals(1, registered.version());
    }

    @Test
    void cycleIsRejectedWithDistinguishableReason() {
        GraphDefinition def = TestSupport.definition("cycle",
                List.of(TestSupport.node("a", "noop"), TestSupport.node("b", "noop"), TestSupport.node("c", "noop")),
                List.of(TestSupport.edge("a", "b"), TestSupport.edge("b", "c"), TestSupport.edge("c", "a")));
        DefinitionException ex = assertThrows(DefinitionException.class, () -> definitions().register(def));
        assertEquals(DefinitionException.Reason.CYCLE_DETECTED, ex.getReason());
    }

    @Test
    void selfLoopIsRejectedAsCycle() {
        GraphDefinition def = TestSupport.definition("self-loop",
                List.of(TestSupport.node("a", "noop")),
                List.of(TestSupport.edge("a", "a")));
        DefinitionException ex = assertThrows(DefinitionException.class, () -> definitions().register(def));
        assertEquals(DefinitionException.Reason.CYCLE_DETECTED, ex.getReason());
    }

    @Test
    void edgeToMissingNodeIsRejected() {
        GraphDefinition def = TestSupport.definition("dangling",
                List.of(TestSupport.node("a", "noop")),
                List.of(TestSupport.edge("a", "ghost")));
        DefinitionException ex = assertThrows(DefinitionException.class, () -> definitions().register(def));
        assertEquals(DefinitionException.Reason.NODE_NOT_FOUND, ex.getReason());
        assertTrue(ex.getMessage().contains("ghost"));
    }

    @Test
    void edgeFromMissingNodeIsRejected() {
        GraphDefinition def = TestSupport.definition("dangling-src",
                List.of(TestSupport.node("a", "noop")),
                List.of(TestSupport.edge("ghost", "a")));
        DefinitionException ex = assertThrows(DefinitionException.class, () -> definitions().register(def));
        assertEquals(DefinitionException.Reason.NODE_NOT_FOUND, ex.getReason());
    }

    @Test
    void duplicateNodeIdIsRejected() {
        NodeDefinition a1 = TestSupport.node("a", "noop");
        NodeDefinition a2 = TestSupport.node("a", "data", Map.of("data", Map.of("x", 1)));
        GraphDefinition def = TestSupport.definition("dup", List.of(a1, a2), List.of());
        DefinitionException ex = assertThrows(DefinitionException.class, () -> definitions().register(def));
        assertEquals(DefinitionException.Reason.DUPLICATE_NODE_ID, ex.getReason());
    }

    @Test
    void rejectedDefinitionIsNotPersisted() {
        GraphDefinition def = TestSupport.definition("rejected",
                List.of(TestSupport.node("a", "noop")),
                List.of(TestSupport.edge("a", "missing")));
        assertThrows(DefinitionException.class, () -> definitions().register(def));
        assertTrue(definitions().list().isEmpty());
    }

    @Test
    void diamondDagIsAccepted() {
        GraphDefinition def = TestSupport.definition("diamond",
                List.of(TestSupport.node("start", "noop"), TestSupport.node("l", "noop"),
                        TestSupport.node("r", "noop"), TestSupport.node("end", "noop")),
                List.of(TestSupport.edge("start", "l"), TestSupport.edge("start", "r"),
                        TestSupport.edge("l", "end"), TestSupport.edge("r", "end")));
        assertEquals(1, definitions().register(def).version());
    }
}

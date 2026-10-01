package com.github.guoyyit.graphnodeexecutionenginejava.engine;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.GraphDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunState;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.runtime.ExecutionEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发测试：独立节点并行推进、同定义同并发度结果一致、输出只被采用一次。
 */
class ConcurrencyTest {

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
    void independentNodesRunInParallel() throws InterruptedException {
        engine = TestSupport.engine(dataDir);
        // start -> {a,b,c} -> end，每个叶子 sleep 300ms
        GraphDefinition def = TestSupport.definition("parallel",
                List.of(TestSupport.node("start", "noop"),
                        TestSupport.node("a", "sleep", Map.of("ms", 300)),
                        TestSupport.node("b", "sleep", Map.of("ms", 300)),
                        TestSupport.node("c", "sleep", Map.of("ms", 300)),
                        TestSupport.node("end", "noop")),
                List.of(TestSupport.edge("start", "a"), TestSupport.edge("start", "b"),
                        TestSupport.edge("start", "c"),
                        TestSupport.edge("a", "end"), TestSupport.edge("b", "end"),
                        TestSupport.edge("c", "end")));
        RunState run = TestSupport.start(engine, def, 3);
        long start = System.currentTimeMillis();
        TestSupport.awaitTerminal(engine, run.getRunId(), Duration.ofSeconds(10));
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(RunStatus.SUCCEEDED, engine.getRun(run.getRunId()).getStatus());
        // 3 个 300ms 并行，总耗时应明显小于串行的 900ms
        assertTrue(elapsed < 700, "expected parallel execution, took " + elapsed + "ms");
    }

    @Test
    void concurrencyOneRunsSerially() throws InterruptedException {
        engine = TestSupport.engine(dataDir);
        GraphDefinition def = TestSupport.definition("serial",
                List.of(TestSupport.node("start", "noop"),
                        TestSupport.node("a", "sleep", Map.of("ms", 200)),
                        TestSupport.node("b", "sleep", Map.of("ms", 200)),
                        TestSupport.node("end", "noop")),
                List.of(TestSupport.edge("start", "a"), TestSupport.edge("start", "b"),
                        TestSupport.edge("a", "end"), TestSupport.edge("b", "end")));
        RunState run = TestSupport.start(engine, def, 1);
        long start = System.currentTimeMillis();
        TestSupport.awaitTerminal(engine, run.getRunId(), Duration.ofSeconds(10));
        long elapsed = System.currentTimeMillis() - start;

        assertEquals(RunStatus.SUCCEEDED, engine.getRun(run.getRunId()).getStatus());
        // 并发度 1：a、b 串行，总耗时应 >= 400ms
        assertTrue(elapsed >= 380, "expected serial execution, took " + elapsed + "ms");
    }

    @Test
    void sameDefinitionAndConcurrencyProducesIdenticalResults() throws InterruptedException {
        engine = TestSupport.engine(dataDir);
        GraphDefinition def = TestSupport.definition("determinism",
                List.of(TestSupport.node("a", "data", Map.of("data", Map.of("v", 1))),
                        TestSupport.node("b", "data", Map.of("data", Map.of("v", 2))),
                        TestSupport.node("c", "spel",
                                Map.of("expression", "#inputs['a']['v'] + #inputs['b']['v']")),
                        TestSupport.node("d", "spel",
                                Map.of("expression", "#inputs['a']['v'] * #inputs['b']['v']"))),
                List.of(TestSupport.edge("a", "c"), TestSupport.edge("b", "c"),
                        TestSupport.edge("a", "d"), TestSupport.edge("b", "d")));

        RunState run1 = TestSupport.start(engine, def, 2);
        TestSupport.awaitTerminal(engine, run1.getRunId(), Duration.ofSeconds(10));
        Map<String, Map<String, Object>> outputs1 = engine.getRun(run1.getRunId()).getOutputs();

        RunState run2 = TestSupport.start(engine, def, 2);
        TestSupport.awaitTerminal(engine, run2.getRunId(), Duration.ofSeconds(10));
        Map<String, Map<String, Object>> outputs2 = engine.getRun(run2.getRunId()).getOutputs();

        assertEquals(outputs1, outputs2);
        assertEquals(Map.of("result", 3), outputs1.get("c"));
        assertEquals(Map.of("result", 2), outputs1.get("d"));
    }

    @Test
    void nodeOutputIsConsumedExactlyOnce() throws Exception {
        engine = TestSupport.engine(dataDir);
        Path effectFile = dataDir.resolve("effects.log");
        GraphDefinition def = TestSupport.definition("once",
                List.of(TestSupport.node("a", "effect", Map.of("path", effectFile.toString())),
                        TestSupport.node("b", "noop"),
                        TestSupport.node("c", "noop")),
                List.of(TestSupport.edge("a", "b"), TestSupport.edge("a", "c")));
        RunState run = TestSupport.start(engine, def, 2);
        TestSupport.awaitTerminal(engine, run.getRunId(), Duration.ofSeconds(10));

        RunState result = engine.getRun(run.getRunId());
        assertEquals(RunStatus.SUCCEEDED, result.getStatus());
        // a 只执行一次，副作用文件只有一行
        List<String> lines = Files.readAllLines(effectFile);
        assertEquals(1, lines.size());
        assertTrue(lines.get(0).startsWith(run.getRunId() + ",a,"));
        // 每个节点的输出只被提交一次
        assertEquals(3, result.getOutputs().size());
    }
}

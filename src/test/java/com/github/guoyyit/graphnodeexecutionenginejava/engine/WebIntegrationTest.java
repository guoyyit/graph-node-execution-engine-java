package com.github.guoyyit.graphnodeexecutionenginejava.engine;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.EdgeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.GraphDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.NodeStatus;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * REST API 集成测试：定义注册、启动运行、查询、取消、恢复端到端可用。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebIntegrationTest {

    @TempDir
    static Path dataDir;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("engine.data-dir", () -> dataDir.toString());
        registry.add("engine.concurrency", () -> 2);
    }

    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    ApplicationContext context;

    @Test
    void fullRunLifecycleViaRest() throws Exception {
        // 注册定义
        GraphDefinition def = new GraphDefinition("web-demo", 1,
                List.of(new NodeDefinition("a", "a", "data", Map.of("data", Map.of("v", 42)),
                        null, 0, null, null),
                        new NodeDefinition("b", "b", "noop", Map.of(), null, 0, null, null)),
                List.of(new EdgeDefinition("a", "b", null)));
        GraphDefinition registered = restTemplate.postForObject(
                "/api/definitions", def, GraphDefinition.class);
        assertNotNull(registered);
        assertEquals(1, registered.version());

        // 启动运行
        Map<String, Object> startRequest = Map.of(
                "definitionName", "web-demo",
                "concurrency", 2);
        Map<?, ?> run = restTemplate.postForObject("/api/runs", startRequest, Map.class);
        assertNotNull(run);
        String runId = (String) run.get("runId");
        assertNotNull(runId);

        // 轮询直到结束
        Map<?, ?> result = null;
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            result = restTemplate.getForObject("/api/runs/" + runId, Map.class);
            String status = (String) result.get("status");
            if (RunStatus.valueOf(status).isTerminal()) {
                break;
            }
            Thread.sleep(50);
        }
        assertNotNull(result);
        assertEquals("SUCCEEDED", result.get("status"));
        Map<?, ?> nodeStates = (Map<?, ?>) result.get("nodeStates");
        assertEquals("SUCCEEDED", ((Map<?, ?>) nodeStates.get("a")).get("status"));
        assertEquals("SUCCEEDED", ((Map<?, ?>) nodeStates.get("b")).get("status"));
    }

    @Test
    void cancelViaRest() throws Exception {
        GraphDefinition def = new GraphDefinition("web-cancel", 1,
                List.of(new NodeDefinition("slow", "slow", "sleep", Map.of("ms", 5000),
                        null, 0, null, null)),
                List.of());
        restTemplate.postForObject("/api/definitions", def, GraphDefinition.class);

        Map<String, Object> startRequest = Map.of("definitionName", "web-cancel");
        Map<?, ?> run = restTemplate.postForObject("/api/runs", startRequest, Map.class);
        String runId = (String) run.get("runId");

        // 等待进入 RUNNING
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            Map<?, ?> current = restTemplate.getForObject("/api/runs/" + runId, Map.class);
            if ("RUNNING".equals(current.get("status"))) {
                break;
            }
            Thread.sleep(20);
        }

        // 取消
        restTemplate.postForObject("/api/runs/" + runId + "/cancel", null, Map.class);

        // 等待最终状态
        Map<?, ?> result = null;
        deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            result = restTemplate.getForObject("/api/runs/" + runId, Map.class);
            String status = (String) result.get("status");
            if (RunStatus.valueOf(status).isTerminal()) {
                break;
            }
            Thread.sleep(20);
        }
        assertNotNull(result);
        assertEquals("CANCELLED", result.get("status"));
        Map<?, ?> nodeStates = (Map<?, ?>) result.get("nodeStates");
        assertEquals("CANCELLED", ((Map<?, ?>) nodeStates.get("slow")).get("status"));
    }

    @Test
    void invalidDefinitionRejectedWith400() {
        // 环
        GraphDefinition def = new GraphDefinition("web-invalid", 1,
                List.of(new NodeDefinition("a", "a", "noop", Map.of(), null, 0, null, null)),
                List.of(new EdgeDefinition("a", "a", null)));
        var response = restTemplate.postForEntity("/api/definitions", def, Map.class);
        assertEquals(400, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("CYCLE_DETECTED", response.getBody().get("reason"));
    }
}

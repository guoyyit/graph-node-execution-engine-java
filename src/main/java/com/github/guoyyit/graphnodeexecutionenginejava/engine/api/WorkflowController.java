package com.github.guoyyit.graphnodeexecutionenginejava.engine.api;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.core.WorkflowEngine;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunSnapshot;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.WorkflowDefinition;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.store.DefinitionStore;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.validation.DefinitionValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 工作流引擎 REST API。 */
@RestController
@RequestMapping("/api")
public class WorkflowController {

    private final WorkflowEngine engine;
    private final DefinitionStore definitionStore;

    public WorkflowController(WorkflowEngine engine, DefinitionStore definitionStore) {
        this.engine = engine;
        this.definitionStore = definitionStore;
    }

    /** 注册新版本定义。校验失败返回 400 与可区分原因。 */
    @PostMapping("/definitions")
    public ResponseEntity<?> register(@RequestBody WorkflowDefinition definition) {
        try {
            WorkflowDefinition saved = engine.registerDefinition(definition);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        } catch (DefinitionValidationException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "reason", e.getReason().name(),
                    "message", String.valueOf(e.getMessage())));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of(
                    "reason", "ALREADY_EXISTS",
                    "message", String.valueOf(e.getMessage())));
        }
    }

    @GetMapping("/definitions/{id}/versions")
    public List<String> versions(@PathVariable String id) {
        return definitionStore.versions(id);
    }

    /** 提交一次运行。 */
    @PostMapping("/runs")
    public ResponseEntity<?> submit(@RequestParam String definitionId,
                                    @RequestParam String version,
                                    @RequestBody(required = false) Map<String, Object> input) {
        try {
            String runId = engine.submit(definitionId, version, input);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("runId", runId));
        } catch (DefinitionValidationException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "reason", e.getReason().name(),
                    "message", String.valueOf(e.getMessage())));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "reason", "NOT_FOUND",
                    "message", String.valueOf(e.getMessage())));
        }
    }

    /** 查询运行状态与每个节点的结果。 */
    @GetMapping("/runs/{runId}")
    public ResponseEntity<RunSnapshot> getRun(@PathVariable String runId) {
        return engine.getRun(runId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** 取消运行（幂等）。 */
    @PostMapping("/runs/{runId}/cancel")
    public ResponseEntity<?> cancel(@PathVariable String runId) {
        if (engine.getRun(runId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        engine.cancel(runId);
        return ResponseEntity.accepted().body(Map.of("runId", runId, "cancelRequested", true));
    }
}

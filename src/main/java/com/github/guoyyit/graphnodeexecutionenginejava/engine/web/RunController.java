package com.github.guoyyit.graphnodeexecutionenginejava.engine.web;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.runtime.ExecutionEngine;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.RunState;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.web.dto.RunResponse;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.web.dto.StartRunRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 运行管理接口：启动、查询、取消、恢复。
 */
@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final ExecutionEngine engine;

    public RunController(ExecutionEngine engine) {
        this.engine = engine;
    }

    @PostMapping
    public RunResponse start(@RequestBody StartRunRequest request) {
        RunState run = engine.startRun(
                request.getDefinitionName(),
                request.getDefinitionVersion(),
                request.getConcurrency(),
                request.getFailureStrategy());
        return RunResponse.from(run);
    }

    @GetMapping
    public List<RunResponse> list() {
        return engine.listRuns().stream().map(RunResponse::from).toList();
    }

    @GetMapping("/{runId}")
    public RunResponse get(@PathVariable String runId) {
        return RunResponse.from(engine.getRun(runId));
    }

    /**
     * 取消运行。幂等。
     */
    @PostMapping("/{runId}/cancel")
    public RunResponse cancel(@PathVariable String runId) {
        return RunResponse.from(engine.cancel(runId));
    }

    /**
     * 恢复未完成运行（进程重启后会自动恢复，此接口用于手动触发）。
     */
    @PostMapping("/{runId}/resume")
    public RunResponse resume(@PathVariable String runId) {
        return RunResponse.from(engine.resume(runId));
    }
}

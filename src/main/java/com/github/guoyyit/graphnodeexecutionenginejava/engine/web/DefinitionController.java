package com.github.guoyyit.graphnodeexecutionenginejava.engine.web;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.def.DefinitionService;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.GraphDefinition;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 执行定义管理接口。
 */
@RestController
@RequestMapping("/api/definitions")
public class DefinitionController {

    private final DefinitionService definitions;

    public DefinitionController(DefinitionService definitions) {
        this.definitions = definitions;
    }

    /**
     * 注册定义：整体校验，非法定义会被拒绝（400）且不会产生任何执行。
     * 版本号由服务端按名称单调分配。
     */
    @PostMapping
    public GraphDefinition register(@RequestBody GraphDefinition definition) {
        return definitions.register(definition);
    }

    @GetMapping
    public List<GraphDefinition> list() {
        return definitions.list();
    }

    @GetMapping("/{name}")
    public GraphDefinition latest(@PathVariable String name) {
        return definitions.getLatest(name)
                .orElseThrow(() -> new IllegalArgumentException("definition not found: " + name));
    }

    @GetMapping("/{name}/versions/{version}")
    public GraphDefinition getVersion(@PathVariable String name, @PathVariable int version) {
        return definitions.get(name, version)
                .orElseThrow(() -> new IllegalArgumentException(
                        "definition not found: " + name + ":" + version));
    }
}

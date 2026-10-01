package com.github.guoyyit.graphnodeexecutionenginejava.engine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.core.WorkflowEngine;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.spi.NodeExecutorRegistry;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.store.DefinitionStore;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.store.FileRunStore;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.store.RunStore;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(EngineProperties.class)
public class EngineConfiguration {

    @Bean
    public NodeExecutorRegistry nodeExecutorRegistry() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        // 内置 echo 执行器：返回节点配置与运行输入，便于联调与演示
        registry.register("echo", ctx -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("config", ctx.config());
            out.put("input", ctx.input());
            return out;
        });
        return registry;
    }

    @Bean
    public DefinitionStore definitionStore(EngineProperties properties, ObjectMapper objectMapper) {
        return new DefinitionStore(Path.of(properties.getDefinitionsDir()), objectMapper);
    }

    @Bean
    public RunStore runStore(EngineProperties properties, ObjectMapper objectMapper) {
        return new FileRunStore(Path.of(properties.getRunsDir()), objectMapper);
    }

    @Bean(destroyMethod = "close")
    public WorkflowEngine workflowEngine(EngineProperties properties,
                                         DefinitionStore definitionStore,
                                         RunStore runStore,
                                         NodeExecutorRegistry registry) {
        return new WorkflowEngine(definitionStore, runStore, registry, properties.getMaxConcurrency());
    }
}

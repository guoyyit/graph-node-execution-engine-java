package com.github.guoyyit.graphnodeexecutionenginejava.engine.config;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.def.DefinitionService;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeExecutorRegistry;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors.DataExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors.EffectExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors.FlakyExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors.NoopExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors.SleepExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors.SpelExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.runtime.ExecutionEngine;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import java.util.List;

/**
 * 引擎装配：内置执行器、定义服务、执行引擎，并在启动后恢复未完成运行。
 */
@Configuration
@EnableConfigurationProperties(EngineProperties.class)
public class EngineConfiguration {

    @Bean
    public NodeExecutorRegistry nodeExecutorRegistry(List<NodeExecutor> customExecutors) {
        NodeExecutorRegistry registry = new NodeExecutorRegistry();
        registry.register(new NoopExecutor());
        registry.register(new DataExecutor());
        registry.register(new SpelExecutor());
        registry.register(new SleepExecutor());
        registry.register(new FlakyExecutor());
        registry.register(new EffectExecutor());
        for (NodeExecutor executor : customExecutors) {
            registry.register(executor);
        }
        return registry;
    }

    @Bean
    public DefinitionService definitionService(EngineProperties properties) {
        return new DefinitionService(properties.dataDirPath());
    }

    @Bean
    public ExecutionEngine executionEngine(EngineProperties properties,
                                           DefinitionService definitionService,
                                           NodeExecutorRegistry registry) {
        return new ExecutionEngine(
                properties.dataDirPath(),
                definitionService,
                registry,
                properties.getConcurrency(),
                properties.getDefaultFailureStrategy(),
                properties.getMaxPoolThreads());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumeOnStartup(ApplicationReadyEvent event) {
        event.getApplicationContext().getBean(ExecutionEngine.class).resumeAllOnStartup();
    }
}

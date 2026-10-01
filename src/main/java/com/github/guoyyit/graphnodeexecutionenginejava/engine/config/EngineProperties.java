package com.github.guoyyit.graphnodeexecutionenginejava.engine.config;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.FailureStrategy;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/**
 * 引擎配置项。
 */
@ConfigurationProperties(prefix = "engine")
public class EngineProperties {

    /** 本地数据目录，定义与运行状态都落盘在此 */
    private String dataDir = "./data";

    /** 默认并发度 */
    private int concurrency = 4;

    /** 节点任务线程池最大线程数 */
    private int maxPoolThreads = 64;

    /** 默认失败策略 */
    private FailureStrategy defaultFailureStrategy = FailureStrategy.ABORT;

    public Path dataDirPath() {
        return Path.of(dataDir);
    }

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(int concurrency) {
        this.concurrency = concurrency;
    }

    public int getMaxPoolThreads() {
        return maxPoolThreads;
    }

    public void setMaxPoolThreads(int maxPoolThreads) {
        this.maxPoolThreads = maxPoolThreads;
    }

    public FailureStrategy getDefaultFailureStrategy() {
        return defaultFailureStrategy;
    }

    public void setDefaultFailureStrategy(FailureStrategy defaultFailureStrategy) {
        this.defaultFailureStrategy = defaultFailureStrategy;
    }
}

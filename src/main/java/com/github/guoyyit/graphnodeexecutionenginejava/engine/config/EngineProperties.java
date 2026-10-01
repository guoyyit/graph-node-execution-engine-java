package com.github.guoyyit.graphnodeexecutionenginejava.engine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 引擎配置项。 */
@ConfigurationProperties(prefix = "engine")
public class EngineProperties {

    /** 单运行内节点级最大并发度 */
    private int maxConcurrency = 4;

    /** 版本化定义存储目录 */
    private String definitionsDir = "data/definitions";

    /** 运行快照存储目录 */
    private String runsDir = "data/runs";

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public String getDefinitionsDir() {
        return definitionsDir;
    }

    public void setDefinitionsDir(String definitionsDir) {
        this.definitionsDir = definitionsDir;
    }

    public String getRunsDir() {
        return runsDir;
    }

    public void setRunsDir(String runsDir) {
        this.runsDir = runsDir;
    }
}

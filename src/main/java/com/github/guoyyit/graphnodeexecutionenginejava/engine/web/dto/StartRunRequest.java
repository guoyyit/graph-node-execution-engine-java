package com.github.guoyyit.graphnodeexecutionenginejava.engine.web.dto;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.model.FailureStrategy;

/**
 * 启动运行请求。
 */
public class StartRunRequest {

    private String definitionName;
    private Integer definitionVersion;
    private Integer concurrency;
    private FailureStrategy failureStrategy;

    public String getDefinitionName() {
        return definitionName;
    }

    public void setDefinitionName(String definitionName) {
        this.definitionName = definitionName;
    }

    public Integer getDefinitionVersion() {
        return definitionVersion;
    }

    public void setDefinitionVersion(Integer definitionVersion) {
        this.definitionVersion = definitionVersion;
    }

    public Integer getConcurrency() {
        return concurrency;
    }

    public void setConcurrency(Integer concurrency) {
        this.concurrency = concurrency;
    }

    public FailureStrategy getFailureStrategy() {
        return failureStrategy;
    }

    public void setFailureStrategy(FailureStrategy failureStrategy) {
        this.failureStrategy = failureStrategy;
    }
}

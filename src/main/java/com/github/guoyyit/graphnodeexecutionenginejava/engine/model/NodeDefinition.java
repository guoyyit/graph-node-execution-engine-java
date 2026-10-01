package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

import java.time.Duration;
import java.util.Map;

/**
 * 节点定义。
 *
 * @param id      节点 id，在同一张图内唯一
 * @param name    节点名称（用于展示）
 * @param type    执行器类型，对应 NodeExecutor#type()
 * @param config  执行器配置（本地来源）
 * @param timeout 节点级超时，null 表示不超时
 * @param retries 失败后最大重试次数（不含首次执行），默认 0
 * @param backoff 重试退避基准时长，默认 1s，按指数增长
 * @param join    汇聚语义，默认 ALL
 */
public record NodeDefinition(
        String id,
        String name,
        String type,
        Map<String, Object> config,
        Duration timeout,
        Integer retries,
        Duration backoff,
        JoinMode join
) {
    public NodeDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("node id must not be blank");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("node type must not be blank: " + id);
        }
    }

    public int retriesOrDefault() {
        return retries == null ? 0 : retries;
    }

    public Duration backoffOrDefault() {
        return backoff == null ? Duration.ofSeconds(1) : backoff;
    }

    public JoinMode joinOrDefault() {
        return join == null ? JoinMode.ALL : join;
    }
}

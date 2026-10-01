package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

import java.util.Map;

/**
 * 节点定义。
 *
 * @param id             节点 id，定义内唯一
 * @param type           节点类型，用于在执行器注册表中查找执行器
 * @param config         节点级配置，原样传给执行器
 * @param timeoutMs      单次尝试超时毫秒数，<=0 表示不限制
 * @param maxRetries     失败后的最大重试次数（不含首次尝试）
 * @param retryBackoffMs 重试退避基数毫秒数，第 n 次重试等待 backoffMs * n
 * @param failurePolicy  重试耗尽后的失败策略
 */
public record NodeDefinition(
        String id,
        String type,
        Map<String, Object> config,
        long timeoutMs,
        int maxRetries,
        long retryBackoffMs,
        FailurePolicy failurePolicy) {

    public NodeDefinition {
        if (config == null) {
            config = Map.of();
        }
        if (failurePolicy == null) {
            failurePolicy = FailurePolicy.ABORT_RUN;
        }
    }
}

package com.github.guoyyit.graphnodeexecutionenginejava.engine.spi;

import java.util.Map;

/**
 * 节点执行上下文。
 *
 * @param runId           运行 id
 * @param nodeId          当前节点 id
 * @param attempt         当前尝试序号（从 1 开始）
 * @param config           节点配置（来自定义）
 * @param input           运行级输入
 * @param upstreamOutputs 上游节点输出快照（nodeId -> output），只读；
 *                        每个上游输出只以不可变副本形式暴露，保证并发下可见结果一致
 */
public record NodeContext(
        String runId,
        String nodeId,
        int attempt,
        Map<String, Object> config,
        Map<String, Object> input,
        Map<String, Map<String, Object>> upstreamOutputs) {

    /** 运行是否已被请求取消，执行器应周期性检查并尽快返回。 */
    public boolean isCancelRequested() {
        return Thread.currentThread().isInterrupted();
    }
}

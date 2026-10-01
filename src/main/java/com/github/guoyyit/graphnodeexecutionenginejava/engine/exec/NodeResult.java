package com.github.guoyyit.graphnodeexecutionenginejava.engine.exec;

import java.util.Map;

/**
 * 节点执行结果。data 即该节点的可见输出，会被持久化并供给下游节点。
 */
public record NodeResult(Map<String, Object> data) {

    public static NodeResult of(Map<String, Object> data) {
        return new NodeResult(data);
    }

    public static NodeResult empty() {
        return new NodeResult(Map.of());
    }
}

package com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeContext;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeResult;

import java.util.Map;

/**
 * 直接返回节点 config 中 data 字段的静态数据执行器。
 */
@SuppressWarnings("unchecked")
public class DataExecutor implements NodeExecutor {

    @Override
    public String type() {
        return "data";
    }

    @Override
    public NodeResult execute(NodeContext context) {
        Object data = context.node().config().get("data");
        if (data instanceof Map<?, ?> map) {
            return NodeResult.of((Map<String, Object>) map);
        }
        return NodeResult.empty();
    }
}

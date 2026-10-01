package com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeContext;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeResult;

import java.util.Map;

/**
 * 空操作执行器，输出空 Map。
 */
public class NoopExecutor implements NodeExecutor {

    @Override
    public String type() {
        return "noop";
    }

    @Override
    public NodeResult execute(NodeContext context) {
        return NodeResult.empty();
    }
}

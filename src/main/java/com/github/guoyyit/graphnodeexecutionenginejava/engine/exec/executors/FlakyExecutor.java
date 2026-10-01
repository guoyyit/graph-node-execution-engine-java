package com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeContext;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeResult;

import java.util.Map;

/**
 * 不稳定执行器：前 config.failTimes 次尝试失败，之后成功，用于重试测试。
 */
public class FlakyExecutor implements NodeExecutor {

    @Override
    public String type() {
        return "flaky";
    }

    @Override
    public NodeResult execute(NodeContext context) {
        int failTimes = Integer.parseInt(
                context.node().config().getOrDefault("failTimes", 1).toString());
        if (context.attempt() <= failTimes) {
            throw new RuntimeException("flaky failure on attempt " + context.attempt());
        }
        return NodeResult.of(Map.of("ok", true, "attempts", context.attempt()));
    }
}

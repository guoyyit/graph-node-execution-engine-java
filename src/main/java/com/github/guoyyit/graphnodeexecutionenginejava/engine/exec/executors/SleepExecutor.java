package com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeContext;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeResult;

import java.util.Map;

/**
 * 休眠执行器，休眠 config.ms 毫秒（默认 100），用于超时/并发测试。
 */
public class SleepExecutor implements NodeExecutor {

    @Override
    public String type() {
        return "sleep";
    }

    @Override
    public NodeResult execute(NodeContext context) throws InterruptedException {
        Object ms = context.node().config().getOrDefault("ms", 100);
        long millis = Long.parseLong(ms.toString());
        Thread.sleep(millis);
        return NodeResult.of(Map.of("sleptMs", millis));
    }
}

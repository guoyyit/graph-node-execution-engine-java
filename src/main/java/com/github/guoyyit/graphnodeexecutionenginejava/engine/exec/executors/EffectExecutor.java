package com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeContext;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * 副作用执行器：向 config.path 文件追加一行（runId,nodeId,时间戳），
 * 用于验证重启恢复后不会重复产生对外效果。
 */
public class EffectExecutor implements NodeExecutor {

    @Override
    public String type() {
        return "effect";
    }

    @Override
    public NodeResult execute(NodeContext context) throws IOException {
        String path = context.node().config().get("path").toString();
        String line = context.runId() + "," + context.nodeId() + "," + System.currentTimeMillis() + "\n";
        Files.writeString(Path.of(path), line,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return NodeResult.of(Map.of("written", true));
    }
}

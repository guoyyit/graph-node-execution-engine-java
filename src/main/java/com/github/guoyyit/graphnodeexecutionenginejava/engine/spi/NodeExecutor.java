package com.github.guoyyit.graphnodeexecutionenginejava.engine.spi;

import java.util.Map;

/**
 * 节点执行器 SPI。实现必须线程安全，且应保证幂等：
 * 进程崩溃恢复时，崩溃前处于 RUNNING 的节点会被重新执行。
 */
public interface NodeExecutor {

    /**
     * 执行节点逻辑。
     *
     * @return 节点输出，会被持久化并以不可变形式提供给下游；返回 null 视为空输出
     */
    Map<String, Object> execute(NodeContext context) throws Exception;
}

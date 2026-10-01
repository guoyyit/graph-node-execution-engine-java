package com.github.guoyyit.graphnodeexecutionenginejava.engine.exec;

/**
 * 节点执行器。实现必须是线程安全的，且对于相同输入应给出相同输出，
 * 以保证同一发货定义在相同并发度下结果一致。
 */
public interface NodeExecutor {

    /** 执行器类型，与 NodeDefinition#type 对应 */
    String type();

    /**
     * 执行节点。
     *
     * @throws Exception 执行失败时抛出，由调度器计入重试
     */
    NodeResult execute(NodeContext context) throws Exception;
}

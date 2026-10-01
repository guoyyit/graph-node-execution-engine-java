package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

/**
 * 节点重试耗尽后的失败策略。
 */
public enum FailureStrategy {
    /** 中止整个运行：不再调度新节点，未完成节点按取消处理 */
    ABORT,
    /** 继续运行：失败节点的下游被跳过，其余独立分支继续执行 */
    CONTINUE
}

package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

/** 节点重试耗尽后的失败策略。 */
public enum FailurePolicy {
    /** 中止整个运行 */
    ABORT_RUN,
    /** 标记该节点失败，下游按跳过处理，运行继续 */
    CONTINUE
}

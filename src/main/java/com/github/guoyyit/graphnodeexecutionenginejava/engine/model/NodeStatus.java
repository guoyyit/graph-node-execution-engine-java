package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

/**
 * 节点在一次运行中的生命周期状态。
 */
public enum NodeStatus {
    /** 尚未被调度 */
    PENDING,
    /** 正在执行（或重试等待退避中） */
    RUNNING,
    /** 执行成功，输出已提交 */
    SUCCEEDED,
    /** 重试耗尽后失败 */
    FAILED,
    /** 因条件不成立或上游失败而被跳过，不会被执行 */
    SKIPPED,
    /** 因运行被取消而终止 */
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == SKIPPED || this == CANCELLED;
    }
}

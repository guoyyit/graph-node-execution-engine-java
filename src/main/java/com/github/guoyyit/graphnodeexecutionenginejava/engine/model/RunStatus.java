package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

/**
 * 一次运行的整体状态。
 */
public enum RunStatus {
    /** 已创建但尚未开始调度 */
    PENDING,
    /** 调度执行中 */
    RUNNING,
    /** 全部节点正常结束 */
    SUCCEEDED,
    /** 存在节点失败且已结束（失败策略为 CONTINUE 时也会是此状态） */
    FAILED,
    /** 被用户取消 */
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}

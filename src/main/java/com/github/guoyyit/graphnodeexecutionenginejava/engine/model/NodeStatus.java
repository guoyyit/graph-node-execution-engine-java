package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

/** 节点在一次运行中的状态。 */
public enum NodeStatus {
    /** 等待依赖满足 */
    PENDING,
    /** 正在执行 */
    RUNNING,
    /** 执行成功 */
    SUCCESS,
    /** 执行失败（重试已耗尽） */
    FAILED,
    /** 因条件分支未命中或上游失败被跳过 */
    SKIPPED,
    /** 因运行被取消而终止 */
    CANCELLED
}

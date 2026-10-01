package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

/** 一次运行的整体状态。 */
public enum RunStatus {
    /** 正在推进 */
    RUNNING,
    /** 全部应执行节点成功 */
    SUCCESS,
    /** 节点失败且失败策略为中止运行 */
    FAILED,
    /** 被取消 */
    CANCELLED
}

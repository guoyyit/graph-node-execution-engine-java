package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

/**
 * 边在一次运行中的解析状态。
 */
public enum EdgeStatus {
    /** 上游尚未结束，边是否成立未知 */
    PENDING,
    /** 上游成功且条件成立（或无条件），边生效 */
    ACTIVE,
    /** 上游被跳过/失败，或条件不成立，边失效 */
    INACTIVE
}

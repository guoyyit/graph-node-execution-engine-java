package com.github.guoyyit.graphnodeexecutionenginejava.engine.validation;

/** 定义校验失败原因，可区分。 */
public enum ValidationFailureReason {
    /** 连线指向不存在的节点 */
    UNKNOWN_NODE,
    /** 定义中存在环 */
    CYCLE_DETECTED,
    /** 节点 id 重复 */
    DUPLICATE_NODE,
    /** 定义缺少必要字段 */
    MALFORMED
}

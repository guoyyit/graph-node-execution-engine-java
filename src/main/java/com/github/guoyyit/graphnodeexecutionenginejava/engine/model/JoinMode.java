package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

/**
 * 汇聚节点的依赖语义。
 */
public enum JoinMode {
    /** 所有前置边都成立才执行，任一边不成立则跳过 */
    ALL,
    /** 至少一条前置边成立即执行，其余不成立的边忽略 */
    ANY
}

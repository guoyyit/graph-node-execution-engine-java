package com.github.guoyyit.graphnodeexecutionenginejava.engine.def;

/**
 * 定义校验异常，携带可区分的失败原因。
 */
public class DefinitionException extends RuntimeException {

    public enum Reason {
        /** 节点 id 重复 */
        DUPLICATE_NODE_ID,
        /** 连线指向不存在的节点 */
        NODE_NOT_FOUND,
        /** 图中存在环 */
        CYCLE_DETECTED
    }

    private final Reason reason;

    public DefinitionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }
}

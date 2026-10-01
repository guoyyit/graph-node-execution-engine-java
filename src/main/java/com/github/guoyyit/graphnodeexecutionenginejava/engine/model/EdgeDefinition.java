package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

/**
 * 带方向的连线定义。
 *
 * @param source    源节点 id
 * @param target    目标节点 id
 * @param condition 条件表达式（SpEL，可使用 #output 引用源节点输出），null 表示无条件恒成立
 */
public record EdgeDefinition(
        String source,
        String target,
        String condition
) {
    public EdgeDefinition {
        if (source == null || source.isBlank()) {
            throw new IllegalArgumentException("edge source must not be blank");
        }
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("edge target must not be blank");
        }
    }

    public String edgeId() {
        return source + "->" + target;
    }
}

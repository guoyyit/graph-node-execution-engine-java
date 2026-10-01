package com.github.guoyyit.graphnodeexecutionenginejava.engine.model;

/**
 * 有向连线定义。
 *
 * @param from      上游节点 id
 * @param to        下游节点 id
 * @param condition 条件表达式，为 null 或空白表示无条件；基于上游节点输出求值
 */
public record EdgeDefinition(String from, String to, String condition) {

    public boolean unconditional() {
        return condition == null || condition.isBlank();
    }
}

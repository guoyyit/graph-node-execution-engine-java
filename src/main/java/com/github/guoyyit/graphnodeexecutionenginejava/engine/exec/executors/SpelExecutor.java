package com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.executors;

import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeContext;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeExecutor;
import com.github.guoyyit.graphnodeexecutionenginejava.engine.exec.NodeResult;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;

/**
 * SpEL 执行器，使用 config.expression 对 #inputs（前置节点输出）与 #node 求值。
 * 求值结果为 Map 时直接作为输出，否则包装为 {result: value}。
 */
public class SpelExecutor implements NodeExecutor {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    @Override
    public String type() {
        return "spel";
    }

    @Override
    @SuppressWarnings("unchecked")
    public NodeResult execute(NodeContext context) {
        Object expression = context.node().config().get("expression");
        if (expression == null || expression.toString().isBlank()) {
            return NodeResult.empty();
        }
        StandardEvaluationContext evalContext = new StandardEvaluationContext();
        evalContext.setVariable("inputs", context.inputs());
        evalContext.setVariable("node", context.node());
        Object value = parser.parseExpression(expression.toString()).getValue(evalContext);
        if (value instanceof Map<?, ?> map) {
            return NodeResult.of((Map<String, Object>) map);
        }
        return NodeResult.of(Map.of("result", value == null ? "" : value));
    }
}

package com.github.guoyyit.graphnodeexecutionenginejava.engine.exec;

import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;

/**
 * 基于 SpEL 的连线条件求值器。
 * 表达式中可使用 #output 引用源节点输出，例如：#output['flag'] == true
 */
public class ConditionEvaluator {

    private final SpelExpressionParser parser = new SpelExpressionParser();

    public boolean evaluate(String condition, Map<String, Object> output) {
        if (condition == null || condition.isBlank()) {
            return true;
        }
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("output", output);
        Boolean result = parser.parseExpression(condition).getValue(context, Boolean.class);
        return Boolean.TRUE.equals(result);
    }
}

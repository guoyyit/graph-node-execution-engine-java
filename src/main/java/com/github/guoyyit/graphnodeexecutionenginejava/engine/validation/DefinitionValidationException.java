package com.github.guoyyit.graphnodeexecutionenginejava.engine.validation;

/** 定义校验失败异常，携带可区分的失败原因。 */
public class DefinitionValidationException extends RuntimeException {

    private final ValidationFailureReason reason;

    public DefinitionValidationException(ValidationFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public ValidationFailureReason getReason() {
        return reason;
    }
}

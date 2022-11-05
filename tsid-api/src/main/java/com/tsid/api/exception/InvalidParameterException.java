package com.tsid.api.exception;

import com.tsid.domain.enums.EErrorActionType;
import org.springframework.validation.Errors;

public class InvalidParameterException extends TSIDServerException {

    private static final long serialVersionUID = -2116671122895194101L;

    private final Errors errors;

    private final EErrorActionType type;

    private final String message;

    public InvalidParameterException(Errors errors, ErrorCode errorCode, EErrorActionType type, String message) {
        super(errorCode);
        this.errors = errors;
        this.type = type;
        this.message = message;
    }

    public Errors getErrors() {
        return this.errors;
    }

    public EErrorActionType getType() { return this.type; }

    public String getMessage() { return this.message; }

}
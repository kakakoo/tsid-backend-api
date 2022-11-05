package com.tsid.api.exception;


import com.tsid.domain.enums.EErrorActionType;

public class TSIDServerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private ErrorCode errorCode;

    private EErrorActionType type;

    public TSIDServerException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public TSIDServerException(ErrorCode errorCode, EErrorActionType type, String message) {
        super(message);
        this.errorCode = errorCode;
        this.type = type;
    }


    public TSIDServerException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public TSIDServerException(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public TSIDServerException(ErrorCode errorCode, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
    }


    public ErrorCode getErrorCode() {
        return errorCode ;
    }

    public EErrorActionType getType() { return type; }
}
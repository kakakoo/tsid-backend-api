package com.tsid.api.exception;

public class InvalidTokenException extends RuntimeException {

    private static final long serialVersionUID = 2L;

    private ErrorCode errorCode;

    public InvalidTokenException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public InvalidTokenException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public InvalidTokenException(ErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    public InvalidTokenException(ErrorCode errorCode, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
    }


    public ErrorCode getErrorCode() {
        return errorCode ;
    }
}

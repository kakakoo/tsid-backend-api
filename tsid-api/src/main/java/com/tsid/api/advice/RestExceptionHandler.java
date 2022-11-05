package com.tsid.api.advice;

import com.tsid.api.exception.ErrorCode;
import com.tsid.api.exception.InvalidParameterException;
import com.tsid.api.exception.InvalidTokenException;
import com.tsid.api.exception.TSIDServerException;
import com.tsid.api.service.ErrorService;
import com.tsid.api.util.Constants;
import com.tsid.domain.enums.EErrorActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@ControllerAdvice
@RequiredArgsConstructor
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    private final ErrorService errorService;

    @ExceptionHandler(InvalidParameterException.class)
    protected ResponseEntity<ErrorResponse> handleInvalidParameterException(InvalidParameterException e) {
        log.error("handleInvalidParameterException", e);

        ErrorResponse response = ErrorResponse
                .builder()
                .type(e.getType())
                .code(e.getErrorCode().getCode())
                .message(e.getMessage())
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidTokenException.class)
    protected ResponseEntity<ErrorResponse> handleInvalidParameterException(InvalidTokenException e) {
        log.error("handleInvalidParameterException", e);

        ErrorResponse response = ErrorResponse
                .builder()
                .type(null)
                .code(e.getErrorCode().getCode())
                .message(e.getMessage())
                .build();

        return new ResponseEntity<>(response, HttpStatus.resolve(e.getErrorCode().getStatus()));
    }

    @ExceptionHandler(TSIDServerException.class)
    protected ResponseEntity<ErrorResponse> handleCustomException(TSIDServerException e) {
        log.error("handleUserAuthServerExcepiton", e);

        ErrorResponse response = ErrorResponse
                .builder()
                .type(e.getType())
                .message(e.getMessage())
                .code(e.getErrorCode().getCode())
                .build();

        return new ResponseEntity(new TSIDServerResponse(response), HttpStatus.OK);
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e, HttpServletRequest request) {
        log.error("handleException", e);

        String message = e.getMessage();
        StackTraceElement[] stackTrace = e.getStackTrace();
        for (StackTraceElement stackTraceElement : stackTrace) {
            String track = stackTraceElement.toString();
            if (track.contains(Constants.SERVER_PACKAGE)) {
                message += "\n" + track;
            }
        }

        String platform = request.getHeader("platform");
        String version = request.getHeader("version");
        errorService.insertErrorLog(platform, version, request.getRequestURI(), message);

        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;
        ErrorResponse response = ErrorResponse
                .builder()
                .type(EErrorActionType.NONE)
                .code(errorCode.getCode())
                .message(e.getMessage())
                .build();

        return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    //RequestBody
    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {

        log.error("handleMissingServletRequestParameterException: ", ex);

        ErrorCode errorCode = ErrorCode.INVALID_PARAMETER;

        ErrorResponse response = ErrorResponse
                .builder()
                .type(EErrorActionType.NONE)
                .code(errorCode.getCode())
                .message(ex.getMessage())
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    protected ResponseEntity<ErrorResponse> handleMethodArgumentTypeMisMatchException(MethodArgumentTypeMismatchException e){
        log.error("handleMethodArgumentTypeMisMatchException: ", e);

        ErrorCode errorCode = ErrorCode.INVALID_PARAMETER;

        ErrorResponse response = ErrorResponse
                .builder()
                .type(EErrorActionType.NONE)
                .code(errorCode.getCode())
                .message(e.getMessage())
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

    /**
     * 파일 업로드 용량 초과시 발생
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    protected ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e) {
        log.info("handleMaxUploadSizeExceededException: ", e);
        ErrorCode errorCode = ErrorCode.INVALID_PARAMETER;

        ErrorResponse response = ErrorResponse
                .builder()
                .type(EErrorActionType.NONE)
                .code(errorCode.getCode())
                .message(e.getMessage())
                .build();

        return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
    }

}
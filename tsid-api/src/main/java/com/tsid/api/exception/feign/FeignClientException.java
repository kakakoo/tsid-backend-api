package com.tsid.api.exception.feign;

import java.util.Collection;
import java.util.Map;

public class FeignClientException extends RuntimeException{
    private final int status;
    private final String errorMessage;

    private final Map<String, Collection<String>> headers;

    // 에러코드와 메세지를 정형화하기 위해 만듬
    private final CustomFeignErrorForm errorForm;

    public FeignClientException(Integer status, String errorMessage, Map<String, Collection<String>> headers
            , CustomFeignErrorForm errorForm) {
        super(errorMessage);
        this.status = status;
        this.errorMessage = errorMessage;
        this.headers = headers;
        this.errorForm = errorForm;
    }

    /**
     * Http Status Code
     * @return
     */
    public Integer getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * FeignResponse Headers
     * @return
     */
    public Map<String, Collection<String>> getHeaders() {
        return headers;
    }

    public CustomFeignErrorForm getErrorForm() {
        return errorForm;
    }
}
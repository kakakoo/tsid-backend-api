package com.tsid.api.exception.feign;

import com.fasterxml.jackson.databind.ObjectMapper;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class FeignClientExceptionErrorDecoder implements ErrorDecoder {

    private ObjectMapper objectMapper = new ObjectMapper();
    private StringDecoder stringDecoder = new StringDecoder();

    @Override
    public FeignClientException decode(String methodKey, Response response) {
        String message = null;
        CustomFeignErrorForm errorForm = null;
        if (response.body() != null) {
            try {
                message = stringDecoder.decode(response, String.class).toString();
                errorForm = objectMapper.readValue(message, CustomFeignErrorForm.class);
            } catch (IOException e) {
                log.error(methodKey + "Error Deserializing response body from failed feign request response.", e);
            }
        }
        return new FeignClientException(response.status(), message, response.headers(), errorForm);
    }
}
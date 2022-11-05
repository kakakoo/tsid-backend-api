package com.tsid.api.exception;

import com.google.gson.Gson;
import com.tsid.domain.enums.EErrorActionType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class JwtAuthenticationException implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e) throws IOException {
        ErrorCode errorCode = ErrorCode.UNAUTHORIZED;

        ErrorResponse res = ErrorResponse.builder()
                .type(EErrorActionType.POPUP)
                .code(errorCode.getCode())
                .message(e.getMessage())
                .build();

        String serializer = new Gson().toJson(res);
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(serializer);
    }
}
package com.tsid.api.exception;

import com.google.gson.Gson;
import com.tsid.domain.enums.EErrorActionType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
public class JwtAccessDenyException implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e) throws IOException, ServletException {
        ErrorCode errorCode = ErrorCode.FORBIDDEN;
        ErrorResponse res = ErrorResponse.builder()
                .message(e.getMessage())
                .code(errorCode.getCode())
                .type(EErrorActionType.NONE)
                .build();

        String serializer = new Gson().toJson(res);
        response.setContentType("application/json;charset=UTF-8");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().write(serializer);
    }
}
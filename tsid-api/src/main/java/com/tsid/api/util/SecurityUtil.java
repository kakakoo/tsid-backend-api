package com.tsid.api.util;

import com.tsid.api.exception.ErrorCode;
import com.tsid.api.exception.TSIDServerException;
import com.tsid.domain.enums.EErrorActionType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Slf4j
public class SecurityUtil {

    private SecurityUtil() { }

    public static String getCurrentUserUuid() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getPrincipal() == null) {
            throw  new TSIDServerException(ErrorCode.GET_PRINCIPAL, EErrorActionType.NONE, "잘못된 토큰입니다.");
        }

        return authentication.getName();
    }
}
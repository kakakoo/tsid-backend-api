package com.tsid.api.util;

import com.tsid.api.exception.ErrorCode;
import com.tsid.api.exception.TSIDServerException;
import com.tsid.domain.enums.EErrorActionType;

public class VersionUtil {

    public static int getVersion(String version) {
        int ver = 0;
        try {
            if (!version.startsWith("v")) {
                throw new TSIDServerException(ErrorCode.INVALID_VERSION, EErrorActionType.NONE, "사용가능한 버전이 아닙니다.");
            }
            ver = Integer.parseInt(version.replace("v", ""));
        } catch (Exception e) {
            throw new TSIDServerException(ErrorCode.INVALID_VERSION, EErrorActionType.NONE, "사용가능한 버전이 아닙니다.");
        }
        return ver;
    }
}

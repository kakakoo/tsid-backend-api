package com.tsid.api.exception.feign;

import com.tsid.domain.enums.EErrorActionType;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CustomFeignErrorForm {
    private Boolean status;
    private EErrorActionType type;
    private String code;
    private String message;

    public CustomFeignErrorForm(Boolean status, EErrorActionType type, String code, String message) {
        this.status = status;
        this.type = type;
        this.code = code;
        this.message = message;
    }
}
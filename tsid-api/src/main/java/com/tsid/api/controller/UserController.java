package com.tsid.api.controller;

import com.tsid.api.exception.ErrorCode;
import com.tsid.api.exception.InvalidParameterException;
import com.tsid.api.service.PushService;
import com.tsid.api.service.UserService;
import com.tsid.domain.enums.EErrorActionType;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;

@ApiResponses({
        @ApiResponse(
                code = 400, message = "Bad Request", response = ErrorDto.class  , responseContainer = "Object"),
        @ApiResponse(
                code = 401, message = "UnAuthorized", response = ErrorDto.class, responseContainer = "Object"),
        @ApiResponse(
                code = 500, message = "Internal Server Error", response = ErrorDto.class  , responseContainer = "Object")
})
@RequestMapping("/api/user")
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PushService pushService;

    @ApiOperation(value = "사용자 알람 여부, 기타 업데이트")
    @PutMapping("/{version}")
    public TSIDServerResponse updateAlarm(...) {

        if (result.hasErrors()) {
            throw new InvalidParameterException(result, ErrorCode.UPDATE, EErrorActionType.NONE, ErrorMessage.INVALID_PARAMETER);
        }

        userService.updateAlarm(...);
        return new TSIDServerResponse();
    }

    @ApiOperation(value = "사용자 개인 정보 업데이트")
    @PutMapping("/{version}/privacy")
    public TSIDServerResponse updatePrivacy(...) {

        userService.updateUserPrivacy(...);
        return new TSIDServerResponse();
    }

    @ApiOperation(value = "장애인 이미지 정보 확인")
    @GetMapping("/{version}/privacy")
    public TSIDServerResponse<UserResponse.UserDisabled> updatePrivacy(...) {

        UserResponse.UserDisabled response = userService.getUserDisabledImage(...);
        return new TSIDServerResponse(response);
    }

    @ApiOperation(value = "유저 정보 조회")
    @GetMapping("/{version}/info")
    public TSIDServerResponse<UserResponse.UserDetail> getUserInfo(...){
        return new TSIDServerResponse(userService.getUserDetail(...));
    }

    @ApiOperation(value = "푸쉬 키 등록/업데이트")
    @PostMapping("/{version}")
    public TSIDServerResponse updatePush(...){

        pushService.updatePushKey(...);
        return new TSIDServerResponse();
    }
}

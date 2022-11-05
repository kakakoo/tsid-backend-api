package com.tsid.api.controller;

import com.tsid.api.exception.ErrorCode;
import com.tsid.api.exception.InvalidParameterException;
import com.tsid.api.service.AuthService;
import com.tsid.domain.enums.EErrorActionType;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

@ApiResponses({
        @ApiResponse(
                code = 400, message = "Bad Request", response = ErrorDto.class  , responseContainer = "Object"),
        @ApiResponse(
                code = 401, message = "UnAuthorized", response = ErrorDto.class, responseContainer = "Object"),
        @ApiResponse(
                code = 500, message = "Internal Server Error", response = ErrorDto.class  , responseContainer = "Object")
})
@RequestMapping("/api/auth")
@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @ApiOperation(value = "TSID 첫 화면 회원가입")
    @PostMapping("/{version}/sign")
    public TSIDServerResponse<TokenResponse.TokenTemp> sign(...){
        if (result.hasErrors()) {
            throw new InvalidParameterException(result, ErrorCode.SIGNUP, EErrorActionType.NONE, ErrorMessage.INVALID_PARAMETER);
        }
        return new TSIDServerResponse(authService.signup(request));
    }

    @ApiOperation(value = "리프레쉬토큰")
    @PostMapping("/{version}/refresh")
    public TSIDServerResponse<TokenResponse.Token> refresh(...){
        if (result.hasErrors()) {
            throw new InvalidParameterException(result, ErrorCode.REFRESH, EErrorActionType.NONE, ErrorMessage.INVALID_PARAMETER);
        }
        return new TSIDServerResponse(authService.refresh(request));
    }

    @ApiOperation(value = "회원탈퇴")
    @DeleteMapping("/{version}/resign")
    public TSIDServerResponse delete(...){
        authService.delete(...);
        return new TSIDServerResponse();
    }

    @ApiOperation(value = "외부 로그인")
    @PostMapping("/{version}/web")
    public TSIDServerResponse<OauthResponse> webOauth(@RequestHeader String Authorization,
                                                      @PathVariable("version") String version,
                                                      @RequestBody OauthRequest token){

        return new TSIDServerResponse(authService.webOauth(Authorization, token));
    }
}

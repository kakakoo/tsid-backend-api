package com.tsid.api.controller;

import com.tsid.api.service.CompanyService;
import com.tsid.api.service.UserService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@ApiResponses({
        @ApiResponse(
                code = 400, message = "Bad Request", response = ErrorDto.class  , responseContainer = "Object"),
        @ApiResponse(
                code = 401, message = "UnAuthorized", response = ErrorDto.class, responseContainer = "Object"),
        @ApiResponse(
                code = 500, message = "Internal Server Error", response = ErrorDto.class  , responseContainer = "Object")
})
@RequestMapping("/api/find")
@RestController
@RequiredArgsConstructor
public class FindController {

    private final UserService userService;
    private final CompanyService companyService;

    @ApiOperation(value = "동의 그룹 가입 사용자 조회")
    @PostMapping("/{version}/user")
    public TSIDServerResponse<UserResponse.UserInfo> findUser(...) {

        return new TSIDServerResponse(userService.findUserByTel(...));
    }

    @ApiOperation(value = "동의 그룹 가입 사용처 조회")
    @GetMapping("/{version}/company")
    public TSIDServerResponse<CompanyResponse.CompanyList> getCompanyList(...){
        return new TSIDServerResponse(companyService.getCompanyList(...));
    }
}

package com.tsid.api.controller;

import com.tsid.api.service.BannerService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@ApiResponses({
        @ApiResponse(
                code = 400, message = "Bad Request", response = ErrorDto.class, responseContainer = "Object"),
        @ApiResponse(
                code = 401, message = "UnAuthorized", response = ErrorDto.class, responseContainer = "Object"),
        @ApiResponse(
                code = 500, message = "Internal Server Error", response = ErrorDto.class, responseContainer = "Object")
})
@RequestMapping("/api/banner")
@RestController
@RequiredArgsConstructor
public class BannerController {

    private final BannerService bannerService;

    @ApiOperation(value = "배너 리스트")
    @GetMapping("/{version}")
    public TSIDServerResponse<BannerResponse.BannerBody> getBanner(@PathVariable("version") String version){

        return new TSIDServerResponse(bannerService.getBannerList());
    }
}

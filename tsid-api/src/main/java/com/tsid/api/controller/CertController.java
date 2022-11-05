package com.tsid.api.controller;

import com.tsid.api.service.CertService;
import com.tsid.api.util.VersionUtil;
import com.tsid.domain.enums.ECertListType;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@ApiResponses({
        @ApiResponse(
                code = 400, message = "Bad Request", response = ErrorDto.class, responseContainer = "Object"),
        @ApiResponse(
                code = 401, message = "UnAuthorized", response = ErrorDto.class, responseContainer = "Object"),
        @ApiResponse(
                code = 500, message = "Internal Server Error", response = ErrorDto.class, responseContainer = "Object")
})
@RequestMapping("/api/cert")
@RestController
@RequiredArgsConstructor
public class CertController {

    private final CertService certService;

    @ApiOperation(value = "인증 리스트")
    @GetMapping("/{version}")
    public TSIDServerResponse<CertResponse.CertList> certList(...) {

        return new TSIDServerResponse<>(certService.getCertList(...));
    }

    @ApiOperation(value = "그룹에서 진행중인 인증 갯수")
    @GetMapping("/{version}/group/{groupId}/count")
    public TSIDServerResponse<CertResponse.ProgressCert> getCertCount(...) {

        return new TSIDServerResponse<>(certService.getProgressCertCount(...));
    }

    @ApiOperation(value = "그룹 인증 리스트")
    @GetMapping("/{version}/group/{groupId}")
    public TSIDServerResponse<CertResponse.CertList> certList(...) {

        return new TSIDServerResponse<>(certService.getCertGroupList(...));
    }

    @ApiOperation(value = "인증 상세")
    @GetMapping("/{version}/{certId}")
    public TSIDServerResponse<CertResponse.CertDetail> certDetail(...) {

        return certService.getCertDetail(...);
    }

    @ApiOperation(value = "인증 처리")
    @PostMapping("/{version}/{certId}")
    public TSIDServerResponse<CertResponse.CertAuthenticate> certAuth(...) {

        int ver = VersionUtil.getVersion(...);
        return new TSIDServerResponse<>(certService.certAuth(...));
    }
}

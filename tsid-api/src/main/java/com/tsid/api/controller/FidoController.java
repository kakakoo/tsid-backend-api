package com.tsid.api.controller;

import com.tsid.api.service.FidoService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@ApiResponses({
        @ApiResponse(
                code = 400, message = "Bad Request", response = ErrorDto.class  , responseContainer = "Object"),
        @ApiResponse(
                code = 401, message = "UnAuthorized", response = ErrorDto.class, responseContainer = "Object"),
        @ApiResponse(
                code = 500, message = "Internal Server Error", response = ErrorDto.class  , responseContainer = "Object")
})
@RequestMapping("/api/fido")
@RestController
@RequiredArgsConstructor
public class FidoController {

    private final FidoService fidoService;

    @ApiOperation(value = "Request a one time challenge for registration.")
    @PostMapping("/{version}/preregister")
    public TSIDServerResponse<FidoResponse.PreResponse> preRegister(...) {

        return new TSIDServerResponse(fidoService.preregister(...));
    }

    @ApiOperation(value = "Submit a one time signed challenge for registration.")
    @PostMapping("/{version}/register")
    public TSIDServerResponse<TokenResponse.TokenLogin> register(...) throws Exception {

        return new TSIDServerResponse<>(fidoService.register(...));
    }

    @ApiOperation(value = "Request a one time challenge for authentication")
    @PostMapping("/{version}/preauthenticate")
    public TSIDServerResponse<FidoResponse.PreResponse> preAuthenticate(...) {

        return new TSIDServerResponse<>(fidoService.preAuthenticate(...));
    }

    @ApiOperation(value = "Submit a one time signed challenge for authentication.")
    @PostMapping("/{version}/authenticate")
    public TSIDServerResponse<FidoResponse.AuthenticateResponse> authenticate(...) throws Exception {

        return new TSIDServerResponse<>(fidoService.authenticate(...));
    }

}

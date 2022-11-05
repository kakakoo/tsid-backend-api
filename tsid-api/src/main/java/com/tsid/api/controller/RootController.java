package com.tsid.api.controller;

import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import springfox.documentation.annotations.ApiIgnore;


@ApiIgnore
@RestController
@Slf4j
@RefreshScope
public class RootController {

    @GetMapping("/")
    @ApiOperation(value = "root", hidden = true)
    public String root() {
        return "";
    }
}

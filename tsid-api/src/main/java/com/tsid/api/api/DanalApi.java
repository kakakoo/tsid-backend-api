package com.tsid.api.api;

import com.tsid.api.config.ServerConfig;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;


@RefreshScope
@FeignClient(name = "danal-auth-api",
        url = "${danal.auth.url}",
        configuration = ServerConfig.class
)
public interface DanalApi {

    @RequestMapping(method = RequestMethod.GET)
    String checkDanalAuth();

    @RequestMapping(method = RequestMethod.POST, value = "/uas",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    String readyDanalCertification(
            @RequestBody DanalRequest.DanalReadyRequest request);

    @RequestMapping(method = RequestMethod.POST, value = "/uas",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    String callbackDanalCertification(
            @RequestBody DanalRequest.DanalCpcgiRequest request);

}

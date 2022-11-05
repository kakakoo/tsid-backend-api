package com.tsid.api.api;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class DanalApiFallback implements FallbackFactory<DanalApi> {

    @Override
    public DanalApi create(Throwable cause) {

        return new DanalApi() {

            @Override
            public String checkDanalAuth() {
                return null;
            }

            @Override
            public String readyDanalCertification(DanalRequest.DanalReadyRequest request) {
                return null;
            }

            @Override
            public String callbackDanalCertification(DanalRequest.DanalCpcgiRequest request) {
                return null;
            }

        };
    }
}

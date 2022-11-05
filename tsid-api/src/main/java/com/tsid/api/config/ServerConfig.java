package com.tsid.api.config;

import com.tsid.api.exception.feign.FeignClientExceptionErrorDecoder;
import feign.Logger;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ServerConfig {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public FeignClientExceptionErrorDecoder commonFeignErrorDecoder() {
        return new FeignClientExceptionErrorDecoder();
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

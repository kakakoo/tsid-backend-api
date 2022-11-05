package com.tsid.api.config;

import com.tsid.api.exception.JwtAccessDenyException;
import com.tsid.api.exception.JwtAuthenticationException;
import com.tsid.api.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    private final TokenUtil tokenProvider;
    private final JwtAuthenticationException jwtAuthenticationException;
    private final JwtAccessDenyException jwtAccessDenyException;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Override
    public void configure(WebSecurity webSecurity) throws Exception {
        webSecurity.ignoring().antMatchers(
                "/v2/api-docs",
                "/configuration/ui",
                "/swagger-resources/**",
                "/configuration/security",
                "/swagger-ui/**",
                "/swagger-ui.html",
                "/css/**",
                "/pub/**",
                "/images/**",
                "/inc/**",
                "/js/**",
                "/webjars/**");
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // CSRF 설정 Disable
        http.csrf().disable()

                // exception handling 할 때 우리가 만든 클래스를 추가
                .exceptionHandling()
                .authenticationEntryPoint(jwtAuthenticationException)
                .accessDeniedHandler(jwtAccessDenyException)

                .and()
                .headers()
                .frameOptions()
                .sameOrigin()

                // 시큐리티는 기본적으로 세션을 사용
                // 여기서는 세션을 사용하지 않기 때문에 세션 설정을 Stateless 로 설정
                .and()
                .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

                .and()
                .authorizeRequests()
                .antMatchers("/").permitAll()
                .antMatchers("/api/fido/*/preregister").permitAll()
                .antMatchers("/api/fido/*/register").permitAll()
                .antMatchers("/api/app/*/check").permitAll()
                .antMatchers("/api/app/*/term").permitAll()
                .antMatchers("/api/app/*/version").permitAll()
                .antMatchers("/api/app/*/guide").permitAll()
                .antMatchers("/api/danal/*/ready").permitAll()
                .antMatchers("/api/danal/*/callback").permitAll()
                .antMatchers("/front/**").permitAll()
                .antMatchers("/api/auth/**").permitAll()

                .anyRequest().authenticated()  // 나머지 API 는 전부 인증 필요

                // JwtFilter 를 addFilterBefore 로 등록했던 JwtSecurityConfig 클래스를 적용
                // JwtFilter 를 addFilterBefore 로 등록했던 JwtSecurityConfig 클래스를 적용
                .and()
                .apply(new JwtSecurityConfig(tokenProvider));
    }
}
package com.tsid.api.util;

import com.tsid.api.exception.ErrorCode;
import com.tsid.api.exception.TSIDServerException;
import com.tsid.domain.enums.EErrorActionType;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Key;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
public class TokenUtil {

    private static final String AUTH_TYPE = "TSID";

    private final Key key;

    public TokenUtil(@Value("${jwt.secret}") String secretKey) {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public Long getUserId(String accessToken){
        String jwt = resolveToken(accessToken);

        Claims claims = parseClaims(jwt);

        return Long.parseLong(claims.getId());
    }

    public String getUserUuid(String accessToken){
        String jwt = resolveToken(accessToken);

        Claims claims = parseClaims(jwt);

        return claims.getId();
    }

    public org.springframework.security.core.Authentication getSecurityAuthentication(String accessToken) {
        Claims claims = parseClaims(accessToken);

        Collection<? extends GrantedAuthority> authorities = Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"));

        UserDetails principal = new User(claims.getId(), claims.getId(), authorities);

        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    public Authentication getAuthentication(String accessToken) {
        String jwt = resolveToken(accessToken);

        Claims claims = parseClaims(jwt);

        return new Authentication(claims.getId(), "www.amlabs.co.kr");
    }

    public TokenDto.TokenLogin generateTokenLogin(Authentication authentication,
                                                  Long accessValidity,
                                                  Long refreshValidity) {

        /**
         * ???????????? ?????? ??????
         */
        long now = (new Date()).getTime();

        Date accessTokenExpiresIn = new Date(now + accessValidity);
        Date refreshTokenExpireIn = new Date(now + refreshValidity);
        String accessToken = Jwts.builder()
                .compact();

        String refreshToken = UUID.randomUUID().toString();

        return TokenDto.TokenLogin.builder()
                .build();
    }

    public TokenDto.Token generateTokenRefresh(Authentication authentication,
                                               Long accessValidity,
                                               Long refreshValidity) {

        long now = (new Date()).getTime();

        Date accessTokenExpiresIn = new Date(now + accessValidity);
        Date refreshTokenExpireIn = new Date(now + refreshValidity);
        String accessToken = Jwts.builder()
                .setId(authentication.getClientId())
                .setExpiration(accessTokenExpiresIn)        // payload "exp": 1516239022 (??????)
                .signWith(key, SignatureAlgorithm.HS512)    // header "alg": "HS512"
                .compact();

        String refreshToken = UUID.randomUUID().toString();

        return TokenDto.Token.builder()
                .build();
    }


    // Request Header ?????? ?????? ????????? ????????????
    public String resolveToken(String accessToken) {
        if (StringUtils.hasText(accessToken) && accessToken.startsWith(AUTH_TYPE)) {
            return accessToken.substring(5);
        }
        return null;
    }



    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
            return true;
        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("????????? JWT ???????????????.");
        } catch (ExpiredJwtException e) {
            log.info("????????? JWT ???????????????.");
        } catch (UnsupportedJwtException e) {
            log.info("???????????? ?????? JWT ???????????????.");
        } catch (IllegalArgumentException e) {
            log.info("JWT ????????? ?????????????????????.");
        }
        return false;
    }

    private Claims parseClaims(String accessToken) {
        try {
            return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(accessToken).getBody();

        } catch (io.jsonwebtoken.security.SecurityException | MalformedJwtException e) {
            log.info("????????? JWT ???????????????.");
            throw new TSIDServerException(ErrorCode.PARSE_CLAIM, EErrorActionType.NONE, "????????? JWT ???????????????.");
        } catch (ExpiredJwtException e) {
            log.info("????????? JWT ???????????????.");
            throw new TSIDServerException(ErrorCode.PARSE_CLAIM, EErrorActionType.NONE, "????????? JWT ???????????????.");
        } catch (UnsupportedJwtException e) {
            log.info("???????????? ?????? JWT ???????????????.");
            throw new TSIDServerException(ErrorCode.PARSE_CLAIM, EErrorActionType.NONE, "???????????? ?????? JWT ???????????????.");
        } catch (IllegalArgumentException e) {
            log.info("JWT ????????? ?????????????????????.");
            throw new TSIDServerException(ErrorCode.PARSE_CLAIM, EErrorActionType.NONE,  "JWT ????????? ?????????????????????.");
        }
    }
}
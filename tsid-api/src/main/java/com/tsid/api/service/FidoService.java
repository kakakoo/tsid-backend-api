package com.tsid.api.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsid.api.exception.ErrorCode;
import com.tsid.api.exception.TSIDServerException;
import com.tsid.api.repo.AuthRepo;
import com.tsid.api.repo.UserRepo;
import com.tsid.api.util.ConvertUtil;
import com.tsid.api.util.EncryptUtil;
import com.tsid.api.util.SecurityUtil;
import com.tsid.api.util.TokenUtil;
import com.tsid.domain.entity.user.User;
import com.tsid.domain.entity.user.UserRepository;
import com.tsid.domain.entity.userAccessToken.UserAccessToken;
import com.tsid.domain.entity.userAccessToken.UserAccessTokenRepository;
import com.tsid.domain.entity.userClientDetail.UserClientDetail;
import com.tsid.domain.entity.userDevice.UserDevice;
import com.tsid.domain.entity.userDevice.UserDeviceRepository;
import com.tsid.domain.entity.userRandomKey.UserRandomKey;
import com.tsid.domain.entity.userRandomKey.UserRandomKeyRepository;
import com.tsid.domain.entity.userRefreshToken.UserRefreshToken;
import com.tsid.domain.entity.userRefreshToken.UserRefreshTokenRepository;
import com.tsid.domain.entity.userTokenHistory.UserTokenHistory;
import com.tsid.domain.entity.userTokenHistory.UserTokenHistoryRepository;
import com.tsid.domain.enums.EAuthenticateFlag;
import com.tsid.domain.enums.EClientPlatformType;
import com.tsid.domain.enums.EErrorActionType;
import com.tsid.domain.enums.token.ETokenType;
import com.tsid.internal.dto.FidoDto;
import com.tsid.internal.dto.res.SKSFResponse;
import com.tsid.internal.sdk.FidoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static com.tsid.internal.util.FidoUtil.RESTRICTED_ANDROID_KEY_POLICY;
import static com.tsid.internal.util.FidoUtil.RESTRICTED_APPLE_POLICY;

@Slf4j
@Service
@RequiredArgsConstructor
public class FidoService {

    private final TokenUtil tokenUtil;
    private final UserRepo userRepo;
    private final AuthRepo authRepo;
    private final FidoClient fidoSdk;

    private final UserRepository userRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final UserAccessTokenRepository userAccessTokenRepository;
    private final UserTokenHistoryRepository userTokenHistoryRepository;
    private final UserRandomKeyRepository userRandomKeyRepository;
    private final UserRefreshTokenRepository userRefreshTokenRepository;

    @Transactional(readOnly = true)
    public FidoResponse.PreResponse preregister(FidoRequest.PreRegister request){

        String policy;
        if (request.getPlatform().equals(EClientPlatformType.I)) {
            policy = RESTRICTED_APPLE_POLICY;
        } else {
            policy = RESTRICTED_ANDROID_KEY_POLICY;
        }

        User userInfo = userRepo.getUserByFidoKey(request.getFidoUserKey());
        if (userInfo == null) {
            throw new TSIDServerException(ErrorCode.CANT_FIND_USER, "존재하지않는 사용자 입니다.");
        }

        String username = "fido key name 생성";
        String displayName = userInfo.getName();

        FidoDto.PreRegister fidoDto = FidoDto.PreRegister.builder()
                .build();

        SKSFResponse preregister = fidoSdk.preregister(fidoDto);
        if (preregister == null || preregister.getError().equals("True")) {
            throw new TSIDServerException(ErrorCode.INTERNAL_SERVER_ERROR, "FIDO-preregister Error");
        }

        FidoResponse.PreResponse response = FidoResponse.PreResponse.builder()
                .build();

        return response;
    }

    @Transactional
    public TokenResponse.TokenLogin register(...) throws Exception {

        String policy;
        if (request.getPlatform().equals(EClientPlatformType.I)) {
            policy = RESTRICTED_APPLE_POLICY;
        } else {
            policy = RESTRICTED_ANDROID_KEY_POLICY;
        }

        User userInfo =userRepo.getUserByFidoKey(request.getFidoUserKey());
        if (userInfo == null) {
            throw new TSIDServerException(ErrorCode.CANT_FIND_USER, "존재하지않는 사용자 입니다.");
        }

        String username = "fido key name 생성";

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> credMap = mapper.readValue(request.getCredResponse(), Map.class);

        credMap.put("username", username);
        credMap.put("policy", policy);

        SKSFResponse registerResponse = fidoSdk.register(credMap);
        if (registerResponse == null || registerResponse.getError().equals("True")) {
            throw new TSIDServerException(ErrorCode.INTERNAL_SERVER_ERROR, "FIDO-register Error");
        }

        userInfo.updateRegisterInfo();
        /**
         * FIDO 등록 완료
         * TSID 토큰 발급
         */
        UserDevice resultUserDevice = userDeviceRepository.findUserDeviceByUserAndDeviceKey(userInfo.getId(), request.getDevice().getKey());
        Authentication authentication = authRepo.getAuthentication(userInfo.getUuid());

        LocalDateTime updateLastLoginDateLocalDateTime = ConvertUtil.convertLocalDateTimeNowTrans();
        Timestamp lastLoginDateTimeStamp = Timestamp.valueOf(updateLastLoginDateLocalDateTime);

        UserDto.ClientDetail clientDetail = ConvertUtil.base64EncodingStrToDecodeClientDetail(secret);

        UserClientDetail resultUserClientDetail = authRepo.getUserClientDetail(clientDetail.getClientId());
        if (resultUserClientDetail == null)
            throw new TSIDServerException(ErrorCode.SIGNUP, EErrorActionType.NONE, "지원하지 않는 어플리케이션입니다");

        resultUserDevice.setUpdateDate(ZonedDateTime.now());

        TokenDto.TokenLogin token = tokenUtil.generateTokenLogin(authentication,
                resultUserClientDetail.getAccessTokenValidity(),
                resultUserClientDetail.getRefreshTokenValidity());

        ZonedDateTime refreshTokenExpireDate = ConvertUtil.convertLongToZonedDateTime(token.getRefreshTokenExpiresIn());

        /**
         * 토큰발행내역이 있으면 회원가입 아님.
         */
        boolean isSign = false;
        UserAccessToken resultUserAccessToken = null;

        List<UserAccessToken> accessTokenList = userAccessTokenRepository.findAccessTokenListByUser(userInfo.getId());
        if (accessTokenList.isEmpty()) {
            isSign = true;
        }

        for (UserAccessToken accessToken : accessTokenList) {
            if (accessToken.getDeviceId().equals(resultUserDevice.getId())) {
                resultUserAccessToken = accessToken;
            }
        }

        if (resultUserAccessToken == null) {
            UserRefreshToken saveUserRefreshToken = UserRefreshToken.builder()
                    .build();
            userRefreshTokenRepository.save(saveUserRefreshToken);

            resultUserAccessToken = UserAccessToken.builder()
                    .build();
            userAccessTokenRepository.save(resultUserAccessToken);
        } else {
            resultUserAccessToken.updateLogin(resultUserDevice.getId(),
                    resultUserClientDetail.getId(),
                    token.getAccessToken(),
                    updateLastLoginDateLocalDateTime,
                    token.getRefreshToken(),
                    refreshTokenExpireDate);
        }

        UserTokenHistory saveUserTokenHistory = UserTokenHistory
                .builder()
                .build();
        userTokenHistoryRepository.save(saveUserTokenHistory);

        token.setDeviceKey(resultUserDevice.getDeviceHash());

        Timestamp createDateTimestamp = Timestamp.valueOf(resultUserAccessToken.getCreateDate());

        String accessKey = "access key 생성";

        token.setAccessKey(accessKey);
        token.setIsSign(isSign);

        return new TokenResponse.TokenLogin(token);
    }

    public FidoResponse.PreResponse preAuthenticate(FidoRequest.PreAuth request){

        String username = "fido key name 생성";
        String policy;
        if (request.getPlatform().equals(EClientPlatformType.I)) {
            policy = RESTRICTED_APPLE_POLICY;
        } else {
            policy = RESTRICTED_ANDROID_KEY_POLICY;
        }

        FidoDto.PreRegister fidoDto = FidoDto.PreRegister.builder()
                .build();

        SKSFResponse preregister = fidoSdk.preAuthenticate(fidoDto);
        if (preregister == null || preregister.getError().equals("True")) {
            throw new TSIDServerException(ErrorCode.INTERNAL_SERVER_ERROR, "FIDO-preAuthenticate Error");
        }

        FidoResponse.PreResponse response = FidoResponse.PreResponse.builder()
                .build();

        return response;
    }

    @Transactional
    public FidoResponse.AuthenticateResponse authenticate(HttpServletRequest httpServletRequest, FidoRequest.Authenticate request) throws Exception {

        String username = "fido key name 생성";
        String policy;
        if (request.getPlatform().equals(EClientPlatformType.I)) {
            policy = RESTRICTED_APPLE_POLICY;
        } else {
            policy = RESTRICTED_ANDROID_KEY_POLICY;
        }

        String clientUserAgent = httpServletRequest.getHeader("User-Agent");

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> credMap = mapper.readValue(request.getCredResponse(), Map.class);

        credMap.put("authFlag", request.getAuthFlag());
        credMap.put("username", username);
        credMap.put("policy", policy);
        credMap.put("clientUserAgent", clientUserAgent);

        SKSFResponse authenticate = fidoSdk.authenticate(credMap);
        if (authenticate == null || authenticate.getError().equals("True")) {
            throw new TSIDServerException(ErrorCode.INTERNAL_SERVER_ERROR, "인증을 다시 시도해주세요.");
        }

        String authKey = "";
        if (request.getAuthFlag().equals(EAuthenticateFlag.CERT)) {
            /**
             * 인증의 경우 인증키 저장
             */
            authKey = authenticate.getResponse();

            User userInfo = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

            UserRandomKey randomKey = UserRandomKey.builder()
                    .build();

            userRandomKeyRepository.save(randomKey);
        }

        FidoResponse.AuthenticateResponse response = FidoResponse.AuthenticateResponse.builder()
                .build();

        return response;
    }
}

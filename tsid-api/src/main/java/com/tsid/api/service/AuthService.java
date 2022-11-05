package com.tsid.api.service;

import com.google.gson.Gson;
import com.tsid.api.exception.ErrorCode;
import com.tsid.api.exception.TSIDServerException;
import com.tsid.api.repo.*;
import com.tsid.api.util.Constants;
import com.tsid.api.util.EncryptUtil;
import com.tsid.api.util.IpUtil;
import com.tsid.api.util.TokenUtil;
import com.tsid.domain.entity.certRole.CertRole;
import com.tsid.domain.entity.company.Company;
import com.tsid.domain.entity.danal.Danal;
import com.tsid.domain.entity.danal.DanalRepository;
import com.tsid.domain.entity.group.Group;
import com.tsid.domain.entity.group.GroupRepository;
import com.tsid.domain.entity.groupCert.GroupCert;
import com.tsid.domain.entity.groupCert.GroupCertRepository;
import com.tsid.domain.entity.groupCertHistory.GroupCertHistory;
import com.tsid.domain.entity.groupCertHistory.GroupCertHistoryRepository;
import com.tsid.domain.entity.groupHasCompany.GroupHasCompany;
import com.tsid.domain.entity.groupHasCompany.GroupHasCompanyRepository;
import com.tsid.domain.entity.groupHasPermission.GroupHasPermission;
import com.tsid.domain.entity.groupHasPermission.GroupHasPermissionRepository;
import com.tsid.domain.entity.permission.Permission;
import com.tsid.domain.entity.term.Term;
import com.tsid.domain.entity.term.TermRepository;
import com.tsid.domain.entity.user.User;
import com.tsid.domain.entity.user.UserRepository;
import com.tsid.domain.entity.userAccessToken.UserAccessToken;
import com.tsid.domain.entity.userAccessToken.UserAccessTokenRepository;
import com.tsid.domain.entity.userClientDetail.UserClientDetail;
import com.tsid.domain.entity.userClientDetail.UserClientDetailRepository;
import com.tsid.domain.entity.userDevice.UserDevice;
import com.tsid.domain.entity.userDevice.UserDeviceRepository;
import com.tsid.domain.entity.userGroupTemp.UserGroupTemp;
import com.tsid.domain.entity.userHasGroup.UserHasGroup;
import com.tsid.domain.entity.userHasGroup.UserHasGroupRepository;
import com.tsid.domain.entity.userHasTerm.UserHasTerm;
import com.tsid.domain.entity.userHasTerm.UserHasTermRepository;
import com.tsid.domain.entity.userNotification.UserNotification;
import com.tsid.domain.entity.userNotification.UserNotificationRepository;
import com.tsid.domain.entity.userNotificationCount.UserNotificationCount;
import com.tsid.domain.entity.userNotificationCount.UserNotificationCountRepository;
import com.tsid.domain.entity.userPrivacy.UserPrivacy;
import com.tsid.domain.entity.userPrivacy.UserPrivacyRepository;
import com.tsid.domain.entity.userTokenHistory.UserTokenHistory;
import com.tsid.domain.entity.userTokenHistory.UserTokenHistoryRepository;
import com.tsid.domain.entity.userWithdraw.UserWithdraw;
import com.tsid.domain.entity.userWithdraw.UserWithdrawRepository;
import com.tsid.domain.enums.*;
import com.tsid.domain.enums.group.EGroupPositionFlag;
import com.tsid.domain.enums.group.EGroupStatusFlag;
import com.tsid.domain.enums.notification.EAlarmFlag;
import com.tsid.domain.enums.notification.ETargetFlag;
import com.tsid.domain.enums.token.ETokenType;
import com.tsid.internal.dto.FidoDto;
import com.tsid.internal.sdk.FidoClient;
import com.tsid.internal.sdk.SnsClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.tsid.api.util.Constants.ALARM_TITLE_CERT;
import static com.tsid.internal.util.FidoUtil.RESTRICTED_ANDROID_KEY_POLICY;
import static com.tsid.internal.util.FidoUtil.RESTRICTED_APPLE_POLICY;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SnsClient snsService;
    private final FidoClient fidoSdk;
    private final CertService certService;
    private final TokenUtil tokenUtil;
    private final UserService userService;

    private final AuthRepo authRepo;
    private final GroupRepo groupRepo;
    private final UserRepo userRepo;
    private final DeviceRepo deviceRepo;
    private final CompanyRepo companyRepo;
    private final CertRepo certRepo;

    private final TermRepository termRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final DanalRepository danalRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final UserAccessTokenRepository userAccessTokenRepository;
    private final UserTokenHistoryRepository userTokenHistoryRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final UserPrivacyRepository userPrivacyRepository;
    private final UserHasTermRepository userHasTermRepository;
    private final UserHasGroupRepository userHasGroupRepository;
    private final GroupCertRepository groupCertRepository;
    private final GroupHasCompanyRepository groupHasCompanyRepository;
    private final GroupHasPermissionRepository groupHasPermissionRepository;
    private final UserWithdrawRepository userWithdrawRepository;
    private final GroupCertHistoryRepository groupCertHistoryRepository;
    private final UserClientDetailRepository userClientDetailRepository;
    private final UserNotificationCountRepository userNotificationCountRepository;

    @Value("${auth.temp.key}")
    private String AUTH_TEMP_KEY;

    @Transactional
    public TokenResponse.Token refresh(String accessToken, TokenRequest.TokenRefresh request){

        /**
         * 토큰의 유효성 검사 및 refresh 가능한지 검증을 마친 이후 재발급
         */

        ........
    }

    @Transactional
    public void delete(String Authorization){

        /**
         * 회원 탈퇴
         * 필요한 회원정보만 회원탈퇴 테이블로 이관한 이후 데이터 제거
         */
        String uuid = tokenUtil.getUserUuid(Authorization);
        User resultUser = userRepository.getUserByUuid(uuid);

        if(resultUser == null){
            throw new TSIDServerException(ErrorCode.DELETE_USER, EErrorActionType.NONE, "존재하지 않거나 탈퇴 처리된 사용자입니다.");
        }

        UserAccessToken accessToken = userAccessTokenRepository.findActiveUserAccessTokenByUser(resultUser.getId());
        if (!accessToken.getToken().equals(tokenUtil.resolveToken(Authorization))) {
            throw new TSIDServerException(ErrorCode.DELETE_USER, EErrorActionType.NONE, "유효하지 않는 토큰입니다.");
        }

        /**
         * user 테이블엔 이름만 남겨놓고 값 삭제
         * user_privacy 는 제거
         * user_withdraw 로 이동
         */
        UserWithdraw withdraw = UserWithdraw.builder()

                .build();
        userWithdrawRepository.save(withdraw);

        resultUser.delete(null);

        List<UserDevice> deviceList = userDeviceRepository.findUserDevices(resultUser.getId());
        for (UserDevice device : deviceList) {
            device.deleteDevice();
        }

        /**
         * 사용자가 MAKER 인 그룹에 속한 사용자들 그룹 탈퇴 처리
         * 사용자가 속한 그룹 탈퇴처리
         * 사용자가 MAKER 인 그룹 isActive = false 처리
         */
        groupRepo.updateMakerWithdraw(resultUser.getId());

        /**
         * 등록된 fido key 제거
         */
        try {
            EClientPlatformType platformType = deviceRepo.getUserPlatform(resultUser.getId(), Authorization);
            String username = EncryptUtil.sha256Encrypt(resultUser.getUuid());
            String policy;
            if (platformType.equals(EClientPlatformType.I)) {
                policy = RESTRICTED_APPLE_POLICY;
            } else {
                policy = RESTRICTED_ANDROID_KEY_POLICY;
            }
            FidoDto.PreRegister fidoDto = FidoDto.PreRegister.builder()
                    .username(username)
                    .policy(policy)
                    .build();
            fidoSdk.deregister(fidoDto);
        } catch (Exception e) {}

        /**
         * 토큰, 푸시정보, 개인정보 제거
         */
        userRepo.deleteUserProcess(resultUser.getId(), resultUser.getUserPrivacy().getId());

    }

    @Transactional
    public TokenResponse.TokenTemp signup(UserRequest.UserSign request) {

        /**
         * 다날을 통해 인증된 사용자인지 체크
         * 디바이스키 중복 확인
         * 사용자 정보 확인 > 회원가입/로그인 체크
         * 정보 처리
         */

        .............
    }

    @Transactional
    public OauthResponse webOauth(...) {

        /**
         * 앱으로 바로 인증 생성
         * 사용자 / 사용처 파악해서 그룹 및 인증 생성
         */
        AuthTokenDto tokenDto = makeToken(token.getToken());

        String certCode = tokenDto.getType();
        String grantType = Constants.AUTHORIZE_GRANT_TYPE;
        if (!certCode.equals(Constants.AUTHORIZE_GRANT_TYPE)) {
            grantType = Constants.CERT_GRANT_TYPE;
        }

        Company companyInfo = companyRepo.getCompanyByClientIdAndCertRole(tokenDto.getClientId(), certCode);
        if (companyInfo == null || !(companyInfo.getStatus().equals(EStatusFlag.ACTIVE) || companyInfo.getStatus().equals(EStatusFlag.HIDE))) {
            throw new TSIDServerException(ErrorCode.INVALID_PARAMETER, EErrorActionType.NONE, "유효하지않은 토큰입니다.");
        }

        User userInfo = userRepository.getUserByUuid(uuid);
        if (companyInfo.getId().equals(Constants.ADMIN_TSID_COMPANY_ID)) {
            /**
             * TSID 관리자 페이지 로그인 시도
             */
            Long hasCompany = companyRepo.getCheckCompanyUser(userInfo.getId());
            if (hasCompany == null || hasCompany == 0) {
                throw new TSIDServerException(ErrorCode.FORBIDDEN_TSID, EErrorActionType.NONE, "사용처에 등록된 직원이 아닙니다.");
            }
        }

        Group groupInfo = groupRepo.getGroupByCompanyAndUser(companyInfo.getId(), userInfo.getId());

        boolean isGroup = false;
        /**
         * 해당 사용처에 대한 그룹이 없으면 관련 데이터 생성
         * 그룹, 사용자-그룹, 그룹-사용처, 그룹-권한
         *
         * 그룹 joinDate 와 인증생성시간 맞추기 위한 nowTime
         */
        ZonedDateTime nowTime = ZonedDateTime.now();
        if (groupInfo == null) {
            String groupName = groupRepo.getGroupName(userInfo.getId(), companyInfo.getName());
            groupInfo = Group.builder()
                    .build();
            groupRepository.save(groupInfo);

            UserHasGroup userHasGroup = UserHasGroup.builder()
                    .build();
            userHasGroupRepository.save(userHasGroup);

            GroupHasCompany groupHasCompany = GroupHasCompany.builder()
                    .build();
            groupHasCompanyRepository.save(groupHasCompany);

            Permission permissionInfo = certRepo.getPermissionByCert();
            GroupHasPermission groupHasPermission = GroupHasPermission.builder()
                    .build();
            groupHasPermissionRepository.save(groupHasPermission);

            /**
             * 그룹을 생성할때, 위임받을 사용처인지 체크
             */
            certService.checkDelegate(userInfo.getId(), companyInfo.getId());

        } else {
            Long userCount = groupRepo.getUserCountInGroup(groupInfo.getId());
            if (userCount > 1) {
                isGroup = true;
            }
        }

        CertRole certRoleInfo = certRepo.getCertRoleByCode(certCode);
        Long callbackId = companyRepo.getCallbackIdByCompanyAndUrl(companyInfo.getId(), tokenDto.getCallbackUri());

        /**
         * 인증 생성
         * stateCode : callback 할때 보내줘야 할 값
         */
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String ipAddress = IpUtil.getIpAddress(request);

        String location = certService.getLocation(ipAddress);
        CertMemberDto certMemberDto = certRepo.getGroupCertMember(groupInfo.getId());

        List<GroupCert> certList = certRepo.getCertListByGroupId(groupInfo.getId());

        int certCount = 1;
        if (certList != null) {
            certCount = certList.size() + 1;
        }

        GroupCert groupCert = GroupCert.builder()
                .build();
        groupCert.setCreateDate(nowTime);
        groupCertRepository.save(groupCert);

        List<UserHasGroup> activeGroupUser = groupRepo.getActiveUserGroupByGroupId(groupInfo.getId());

        for (UserHasGroup userHasGroup : activeGroupUser) {
            GroupCertHistory groupCertHistory = GroupCertHistory.builder()
                    .build();
            groupCertHistoryRepository.save(groupCertHistory);
        }

        List<Long> userIds = activeGroupUser.stream()
                .map(p -> p.getUser().getId()).collect(Collectors.toList());
        /**
         * 알림 등록
         */
        ETargetFlag targetFlag = ETargetFlag.ofName(grantType);

        String content = "<font color='#3881ED'><b>" + groupInfo.getName() + "</b></font>" +
                " 에서 <b><u>인증요청</b></u>이 왔습니다.";
        for (Long userId : userIds) {
            UserNotification noti = UserNotification.builder()
                    .build();
            userNotificationRepository.save(noti);
        }

        userService.insertUserActionLog(userInfo.getId(), EActionFlag.CERT_MAKE, groupCert.getId());

        if (isGroup) {
            /**
             * 그룹 사용자에게 push 보내기
             */
            String pushTitle = "인증요청 : " + certRoleInfo.getName();
            String pushMessage = companyInfo.getName() + " 에서 인증요청이 왔습니다!";

            PushDto.PushData payload = PushDto.PushData.builder()
                    .build();

            snsService.sendPush(new Gson().toJson(payload), userIds);
        }

        return OauthResponse.builder()
                .build();
    }

    private AuthTokenDto makeToken(String requestToken) {

        /**
         * tsid 에서 만들어진 토큰이 맞는지 검증
         */

        ..................

        return AuthTokenDto.builder()
                .build();
    }
}

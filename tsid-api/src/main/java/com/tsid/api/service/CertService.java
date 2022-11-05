package com.tsid.api.service;

import com.google.gson.Gson;
import com.tsid.api.exception.ErrorCode;
import com.tsid.api.exception.InvalidTokenException;
import com.tsid.api.exception.TSIDServerException;
import com.tsid.api.repo.CertRepo;
import com.tsid.api.repo.DeviceRepo;
import com.tsid.api.repo.GroupRepo;
import com.tsid.api.util.*;
import com.tsid.domain.entity.companyCallback.CompanyCallback;
import com.tsid.domain.entity.companyCallback.CompanyCallbackRepository;
import com.tsid.domain.entity.geoIpBlock.GeoIpBlock;
import com.tsid.domain.entity.group.Group;
import com.tsid.domain.entity.group.GroupRepository;
import com.tsid.domain.entity.groupCert.GroupCert;
import com.tsid.domain.entity.groupCert.GroupCertRepository;
import com.tsid.domain.entity.groupCertHistory.GroupCertHistory;
import com.tsid.domain.entity.groupCertHistory.GroupCertHistoryRepository;
import com.tsid.domain.entity.groupUpdateHistory.GroupUpdateHistory;
import com.tsid.domain.entity.oauthAuthorizeLog.OauthAuthorizeLog;
import com.tsid.domain.entity.oauthAuthorizeLog.OauthAuthorizeLogRepository;
import com.tsid.domain.entity.user.User;
import com.tsid.domain.entity.user.UserRepository;
import com.tsid.domain.entity.userAccessToken.UserAccessToken;
import com.tsid.domain.entity.userAccessToken.UserAccessTokenRepository;
import com.tsid.domain.entity.userGroupHistory.UserGroupHistory;
import com.tsid.domain.entity.userGroupHistory.UserGroupHistoryRepository;
import com.tsid.domain.entity.userHasGroup.UserHasGroup;
import com.tsid.domain.entity.userHasGroup.UserHasGroupRepository;
import com.tsid.domain.entity.userNotification.UserNotification;
import com.tsid.domain.entity.userNotification.UserNotificationRepository;
import com.tsid.domain.entity.userRandomKey.UserRandomKey;
import com.tsid.domain.entity.userRandomKey.UserRandomKeyRepository;
import com.tsid.domain.enums.EActionFlag;
import com.tsid.domain.enums.ECertListType;
import com.tsid.domain.enums.EStatusFlag;
import com.tsid.domain.enums.cert.ECertFlag;
import com.tsid.domain.enums.cert.ECertHistoryFlag;
import com.tsid.domain.enums.group.*;
import com.tsid.domain.enums.notification.EAlarmFlag;
import com.tsid.domain.enums.notification.ETargetFlag;
import com.tsid.domain.enums.token.ETokenStatusFlag;
import com.tsid.internal.sdk.SnsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.springframework.data.domain.Pageable;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.tsid.api.util.CommonUtil.getRandomCode;
import static com.tsid.api.util.CommonUtil.nameConvertMasking;
import static com.tsid.api.util.Constants.ALARM_TITLE_CERT;

@Slf4j
@Service
@RequiredArgsConstructor
public class CertService {

    private final SnsClient snsService;
    private final CertRepo certRepo;
    private final GroupRepo groupRepo;
    private final DeviceRepo deviceRepo;

    private final UserService userService;

    private final GroupRepository groupRepository;
    private final UserAccessTokenRepository userAccessTokenRepository;
    private final UserRepository userRepository;
    private final UserHasGroupRepository userHasGroupRepository;
    private final UserRandomKeyRepository userRandomKeyRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final GroupCertRepository groupCertRepository;
    private final GroupCertHistoryRepository groupCertHistoryRepository;
    private final OauthAuthorizeLogRepository oauthAuthorizeLogRepository;
    private final CompanyCallbackRepository companyCallbackRepository;
    private final UserGroupHistoryRepository userGroupHistoryRepository;

    @Transactional
    public CertResponse.CertList getCertList(...) {

        /**
         * 인증되지 않은 리스트는 상단에,
         * 인증된 리스트는 최근순 관
         */
        List<CertDto.Cert> certList = certRepo.getCertList(SecurityUtil.getCurrentUserUuid(), type, pageable);
        return new CertResponse.CertList(certList);
    }

    @Transactional
    public TSIDServerResponse<CertResponse.CertDetail> getCertDetail(...) {
        /**
         * 인증 상세 내역
         * 해당 인증과 관련되었는지 확인
         * 해당 인증 내역 확인
         */
        if (!token.getStatus().equals(ETokenStatusFlag.ACTIVE)) {

            String message = "다른 기기에서 로그인했습니다.\n다시 로그인해주세요.\n";

            String device = deviceRepo.findOtherLoginDevice(token.getUserId());
            String updateDate = ConvertUtil.localDateTimeToString(token.getUpdateDate(), "yyyy.MM.dd HH:mm:ss");
            if (device != null) {
                message = message + device + "\n";
            }
            message += updateDate;

            throw new InvalidTokenException(ErrorCode.OTHER_LOGIN, message);
        }

        User userInfo = userRepository.getUserById(token.getUserId());
        CertDto.CertDetail certDetail = certRepo.getCertDetailWithMakerByCertId(certId, userInfo.getId());

        if (certDetail == null) {
            throw new TSIDServerException(ErrorCode.CERT_NOT_EXIST, "존재하지않는 인증입니다.");
        }

        /**
         * 해당 인증 관련 알림 읽음 처리
         */
        userNotificationRepository.updateUserNotificationByCertIdAndUserId(certId, userInfo.getId());

        UserHasGroup groupUser = userHasGroupRepository.getUserHasGroupByCertIdAndUserId(certId, userInfo.getId());
        if (groupUser == null) {
            ErrorResponse error = new ErrorResponse();
            error.setCode(ErrorCode.CERT_NOT_EXIST.getCode());
            error.setMessage("인증할 수 없는 사용자 입니다.");
            return new TSIDServerResponse<>(error);
        }

        String message;
        boolean isMyAuth = false;
        boolean isMaker = false;
        boolean isExpire = false;

        if (userInfo.getId() == certDetail.getMakerId()) {
            isMaker = true;
        }

        if (certDetail.getStatus().equals(ECertFlag.SUCCESS)) {
            message = "인증 요청이 <font color='#FF0045'>완료</font> 되었습니다.";
        } else if (certDetail.getStatus().equals(ECertFlag.EXPIRED)) {
            isExpire = true;
            message = "요청된 인증 시간이\n" +
                    "<font color='#FF0045'>만료</font> 되었습니다.";
        } else if (certDetail.getStatus().equals(ECertFlag.REJECT)) {
            message = "인증 요청이 <font color='#FF0045'>거절</font> 되었습니다.";
        } else {
            if (ZonedDateTime.now().isAfter(certDetail.getExpireDate())) {
                isExpire = true;
                certDetail.setStatus(ECertFlag.EXPIRED);
                message = "요청된 인증 시간이\n" +
                        "<font color='#FF0045'>만료</font> 되었습니다.";

                groupCertRepository.updateGroupCertExpiredByGroupCertId(certDetail.getId());

                /**
                 * 해당 인증 관련 만료시킬 위임건 있는지 확인
                 */
                groupRepo.updateGroupUpdateHistoryExpired(certDetail.getId());
            } else {
                message = "인증이 <font color='#FF0045'>진행중</font> 입니다.";
                if (certDetail.getIsGroup()) {
                    message = "그룹 " + message;
                }
                GroupCertHistory groupCertHistory = certDetail.getGroupCertHistory();
                if (groupCertHistory.getStatus().equals(ECertHistoryFlag.BEFORE)) {
                    if (isMaker) {
                        message = "인증 <font color='#FF0045'>요청</font>이 왔습니다.";
                    }
                } else {
                    /**
                     * 본인은 인증 완료
                     */
                    isMyAuth = true;
                }
            }
        }

        boolean isReferrer = false;
        String referrerName = null;
        if (groupUser.getPosition().equals(EGroupPositionFlag.REFERRER)) {
            isReferrer = true;
            referrerName = CommonUtil.nameConvertMasking(userInfo.getName());
        }

        List<UserDto.UserCertHistory> certHistoryList = new ArrayList<>();
        String failMessage = "";
        String makerName = "";
        String makerTel = "";
        String certDevice = null;
        ZonedDateTime certDate = null;

        if (encKey != null) {
            certHistoryList = certRepo.getUserCertHistoryByCertId(certId);
            String decKey = EncryptUtil.rsaDecrpyt(encKey);
            /**
             * 인증이 진행중일 경우
             * 그룹 사용자 인증 내역 추가
             */
            if (certDetail.getStatus().equals(ECertFlag.PROGRESS)) {

                for (UserDto.UserCertHistory userCertHistory : certHistoryList) {
                    if (userCertHistory.getStatus().equals(EStatusFlag.ACTIVE)) {
                        if (userInfo.getId().equals(userCertHistory.getUserId())) {
                            userCertHistory.setIsMine(true);
                        }
                        userCertHistory.setUserName(CommonUtil.nameConvertMasking(userCertHistory.getUserName()));
                        userCertHistory.setUserTel(EncryptUtil.aes256Encrypt(decKey, userCertHistory.getUserTel()));
                    } else {
                        userCertHistory.setUserName("***");
                        userCertHistory.setUserTel(EncryptUtil.aes256Encrypt(decKey, "탈퇴한 회원"));
                    }
                    if (userCertHistory.getCertStatus().equals(ECertHistoryFlag.SUCCESS)) {
                        userCertHistory.setIsCert(true);
                    } else if (!userCertHistory.getCertStatus().equals(ECertHistoryFlag.BEFORE)) {
                        userCertHistory.setIsCert(false);
                    }
                }

            }

            UserDto.UserCertHistory certHistory = certHistoryList.stream()
                    .filter(m -> m.getUserId().equals(userInfo.getId())).findFirst().orElse(null);
            if (certHistory != null) {
                certDevice = certHistory.getCertDevice();
                certDate = certHistory.getCertDate();
            }

            /**
             * 만료 / 거절 시 사유 작성
             * 만료 : 만료시간 초과 (인증시간)
             * 거절 : 김*서 거절 / 김*서 외 2명 거절
             */
            if (certDetail.getStatus().equals(ECertFlag.EXPIRED)) {
                int timer = certHistoryList.size() * 180;
                failMessage = "인증시간 초과 (" + timer + "초)";
            } else if (certDetail.getStatus().equals(ECertFlag.REJECT)) {
                int rejectCnt = 0;
                for (UserDto.UserCertHistory userCertHistory : certHistoryList) {
                    if (!userCertHistory.getIsCert()) {
                        if (rejectCnt == 0) {
                            failMessage = CommonUtil.nameConvertMasking(userCertHistory.getUserName());
                        }
                        rejectCnt++;
                    }
                }
                if (rejectCnt > 1) {
                    rejectCnt = rejectCnt - 1;
                    failMessage += " 외 " + rejectCnt + "명";
                }
                failMessage += " 거절";
            }

            if (!certDetail.getStatus().equals(ECertFlag.PROGRESS)) {
                certHistoryList = new ArrayList<>();
            }

            makerName = CommonUtil.nameConvertMasking(certDetail.getMakerName());
            makerTel = EncryptUtil.aes256Encrypt(decKey, certDetail.getMakerTel());
        }

        /**
         * TODO: 임시로 일단 대한민국 으로만 뜨도록
         */
        String tempLocation = "대한민국";

        CertResponse.CertDetail response = CertResponse.CertDetail.builder()
                .build();

        if (certDetail.getIsCert()) {
            response.setCertDate(certDate == null ? certDetail.getUpdateDate() : certDate);
            response.setCertLocation(tempLocation);
            response.setCertDevice(certDevice == null ? certDetail.getCertDevice() : certDevice);
        }

        return new TSIDServerResponse<>(response);
    }

    private String getGroupCertName(EUpdateFlag flag) {
        String certName = "";
        switch (flag) {
            case DELEGATE:
                certName = "위임 대상";
                break;
            case WITHDRAW:
                certName = "해제 대상";
                break;
            case TO_REFERRER:
                certName = "참조 대상";
                break;
        }
        return certName;
    }

    @Transactional
    public CertResponse.CertAuthenticate certAuth(...) {
        /**
         * 인증하기
         * 인증전 체크 사항
         * 1. 사용자 토큰이 유효한지
         * 2. 인증 키 값이 유효한지
         * 3. 해당 인증이 유효한지
         * 4. 해당 사용자가 참조자인지
         * 5. 해당 인증에 대해 사용자가 인증을 한 이력이 있는지
         */
        User userInfo = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        UserAccessToken userToken = userAccessTokenRepository.findUserAccessTokenByUserIdAndDeviceKey(userInfo.getId(), request.getDeviceKey());
        if (userToken == null) {
            throw new TSIDServerException(ErrorCode.CERT_ALREADY_DONE, "유효하지 않은 토큰입니다.");
        }

        UserRandomKey keyInfo = userRandomKeyRepository.getUserRandomKeyByUserIdAndKey(userInfo.getId(), request.getAuthenticateKey());
        if (keyInfo == null) {
            throw new TSIDServerException(ErrorCode.CERT_AUTH_REJECT, "유효하지 않은 인증입니다.");
        }

        GroupCert cert = groupCertRepository.getGroupCertByCertId(certId);
        if (cert == null) {
            throw new TSIDServerException(ErrorCode.CERT_NOT_EXIST, "존재하지 않는 인증입니다.");
        }
        if (cert.getIsCert()) {
            throw new TSIDServerException(ErrorCode.CERT_ALREADY_DONE, "인증이 이미 완료되었습니다.");
        }

        GroupCertHistory certHistories = groupCertHistoryRepository.getGroupCertHistoryByUserIdAndCertId(userInfo.getId(), certId);
        if (certHistories == null) {
            throw new TSIDServerException(ErrorCode.CERT_ALREADY_DONE, "인증 권한이 없습니다.");
        }
        if (!certHistories.getStatus().equals(ECertHistoryFlag.BEFORE)) {
            throw new TSIDServerException(ErrorCode.CERT_ALREADY_DONE, "이미 인증을 하셨습니다.");
        }
        if (certHistories.getPosition().equals(EGroupPositionFlag.REFERRER)) {
            throw new TSIDServerException(ErrorCode.CERT_REFERRER_CANT, "참조자는 인증을 할 수 없습니다.");
        }

        /**
         * 승인인지, 거절인지
         * 어떤 휴대기기로 인증하는지
         */
        boolean doCert = true;
        if (request.getIsCert() != null) {
            doCert = request.getIsCert();
        }
        String device = deviceRepo.getDeviceModelByModel(request.getDeviceModel());

        HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String ipAddress = IpUtil.getIpAddress(req);
        String location = getLocation(ipAddress);

        certHistories.updateCert(doCert, device, ipAddress, location);

        boolean isCert = false;
        boolean isDone = false;
        boolean isReject = false;

        List<Long> historyUserIds = null;
        if (cert.getIsGroup()) {
            /**
             * 인증이 끝났는지 확인
             * 인증 횟수가 채워지면 거절이 있는지 체크해서 업데이트
             */
            List<GroupCertHistory> certHistoryList = groupCertHistoryRepository.getCertGroupCertHitoryByCertId(certId);
            historyUserIds = certHistoryList.stream()
                    .map(p -> p.getUser().getId()).collect(Collectors.toList());
            historyUserIds.remove(userInfo.getId());

            if (certHistoryList.size() >= cert.getCertValue()) {
                isDone = true;
                int rejectCnt = 0;
                for (GroupCertHistory groupCertHistory : certHistoryList) {
                    if (groupCertHistory.getStatus().equals(ECertHistoryFlag.REJECT) ||
                            groupCertHistory.getStatus().equals(ECertHistoryFlag.CANCEL)) {
                        rejectCnt++;
                    }
                }
                if (rejectCnt > 0) {
                    isReject = true;
                    cert.updateCertAuth(ECertFlag.REJECT);
                } else {
                    cert.updateCertAuth(ECertFlag.SUCCESS);
                    isCert = true;
                }
            }
        } else {
            /**
             * 개인 인증
             */
            if (doCert) {
                cert.updateCertAuth(ECertFlag.SUCCESS);
                isCert = true;
            } else {
                cert.updateCertAuth(ECertFlag.REJECT);
            }
            isDone = true;
        }

        if (doCert) {
            // 로그 등록
            userService.insertUserActionLog(userInfo.getId(), EActionFlag.CERT, certId);
        } else {
            // 로그 등록
            userService.insertUserActionLog(userInfo.getId(), EActionFlag.CERT_REFUSE, certId);
        }

        /**
         * 그룹장인지 확인
         * 그룹장이면 위치정보 기록
         */
        CertDto.CertSns certSns = certRepo.getCertSnsInfoByCertId(certId);
        Long makerId = certSns.getUserId();

        if (makerId.equals(userInfo.getId())) {
            cert.updateCertLocation(ipAddress, location, device);
        }

        CertResponse.CertAuthenticate response = CertResponse.CertAuthenticate.builder().build();
        if (isDone) {
            String title = "인증 완료";
            String content = certSns.getName() + " 에서 요청된 인증이 완료되었습니다.";
            String alarmContent = "<font color='#3881ED'><b>" + certSns.getName() + "</b></font> 에서 요청된 인증이 <b><u>완료</b></u>되었습니다.";
            if (!isCert) {
                title = "인증 거절";
                content = certSns.getName() + " 에서 요청된 인증이 거절되었습니다.";
                alarmContent = "<font color='#3881ED'><b>" + certSns.getName() + "</b></font> 에서 요청된 인증이 <b><u>거절</b></u>되었습니다.";
            }

            String roleCode = certRepo.getCertRoleCodeByCertId(certId);

            if (roleCode.equals(Constants.GROUP_DELEGATE_GRANT_TYPE) ||
                    roleCode.equals(Constants.GROUP_TO_REFERRER_GRANT_TYPE) ||
                    roleCode.equals(Constants.GROUP_WITHDRAW_GRANT_TYPE) ||
                    roleCode.equals(Constants.GROUP_DESTROY_GRANT_TYPE)) {

                /**
                 * 그룹장이 변경을 위한 인증을 생성한 경우
                 * 해당 내용 관련된 액션 진행
                 */
                GroupUpdateHistory groupUpdateHistory = certRepo.getGroupUpdateHistory(cert.getId());
                if (isReject) {
                    groupUpdateHistory.updateStatus(EGroupUpdateFlag.CANCEL);
                } else {
                    makeGroupUpdate(groupUpdateHistory);
                }

                title = groupUpdateTitle(groupUpdateHistory.getFlag(), groupUpdateHistory.getStatus());
                content = certSns.getName() + groupUpdateContent(groupUpdateHistory.getFlag(), groupUpdateHistory.getStatus(), false);
                alarmContent = "<font color='#3881ED'><b>" + certSns.getName() + "</b></font>" + groupUpdateContent(groupUpdateHistory.getFlag(), groupUpdateHistory.getStatus(), true);

            } else {
                String code = getRandomCode();
                if (roleCode.equals(Constants.AUTHORIZE_GRANT_TYPE)) {

                    OauthAuthorizeLog log = OauthAuthorizeLog.builder()
                            .userId(makerId)
                            .companyId(cert.getCompany().getId())
                            .tokenCode(code)
                            .build();
                    oauthAuthorizeLogRepository.save(log);
                } else {
                    /**
                     * 인가 외 인증관련
                     * callback url 로 POST 전송
                     */
                    CompanyCallback callback = companyCallbackRepository.getCompanyCallbackByIdAndCompanyId(cert.getCallbackId(), cert.getCompany().getId());

                    String time = String.valueOf(System.currentTimeMillis());
                    sendCallbackPOST(callback.getCallback(), cert.getStateCode(), code, time);
                    response = CertResponse.CertAuthenticate.builder()
                            .targetUrl(callback.getCallback())
                            .state(cert.getStateCode())
                            .platform("MOBILE")
                            .code(code)
                            .type(time)
                            .build();
                }
            }

            /**
             * sns / notification
             */
            UserNotification noti = UserNotification.builder()
                    .alarmFlag(EAlarmFlag.AUTH)
                    .targetFlag(ETargetFlag.NONE)
                    .title(ALARM_TITLE_CERT)
                    .content(alarmContent)
                    .targetId(certId)
                    .build();

            if (cert.getIsGroup()) {
                /**
                 * 인증 요청자에게 푸시 보내기
                 * 알림 등록하기
                 */
                noti.setUserId(makerId);
                userNotificationRepository.save(noti);

                PushDto.PushData payload = PushDto.PushData.builder()
                        .title(title)
                        .message(content)
                        .alarmFlag(EAlarmFlag.AUTH)
                        .targetFlag(ETargetFlag.CERT)
                        .targetId(certId)
                        .build();

                List<Long> userIds = new ArrayList<>();
                userIds.add(makerId);

                snsService.sendPush(new Gson().toJson(payload), userIds);
            } else {
                noti.setUserId(userInfo.getId());
                userNotificationRepository.save(noti);
            }
        }
        keyInfo.usedKey();

        if (cert.getIsGroup() && !historyUserIds.isEmpty()) {
            /**
             * 백그라운드 푸시 보내기
             */
            PushDto.PushData payload = PushDto.PushData.builder()
                    .alarmFlag(EAlarmFlag.REFRESH)
                    .targetFlag(ETargetFlag.CERT_DETAIL)
                    .targetId(certId)
                    .build();

            snsService.sendPush(new Gson().toJson(payload), historyUserIds);
        }

        return response;
    }

    private String groupUpdateContent(EUpdateFlag flag, EGroupUpdateFlag status, boolean isHtml) {

        String content = " 에서 요청된 ";

        switch (flag) {
            case DELEGATE:
                content += "그룹장 위임이 ";
                break;
            case TO_REFERRER:
                content += "참조자로 변경이 ";
                break;
            case DESTROY:
                content += "그룹 제거가 ";
                break;
            case WITHDRAW:
                content += "사용자 해제가 ";
                break;
        }

        String result;
        if (status.equals(EGroupUpdateFlag.DONE)) {
            result = "완료";
        } else {
            result = "취소";
        }
        if (isHtml) {
            result = "<b><u>" + result + "</u></b>";
        }
        return content + result + "되었습니다.";
    }

    private String groupUpdateTitle(EUpdateFlag flag, EGroupUpdateFlag status) {

        String title = "";
        switch (flag) {
            case DELEGATE:
                title = "그룹장 위임 ";
                break;
            case TO_REFERRER:
                title = "참조자로 변경 ";
                break;
            case DESTROY:
                title = "그룹 제거 ";
                break;
            case WITHDRAW:
                title = "사용자 해제 ";
                break;
        }

        if (status.equals(EGroupUpdateFlag.DONE)) {
            title += "완료";
        } else {
            title += "취소";
        }
        return title;
    }

    private void makeGroupUpdate(GroupUpdateHistory groupUpdateHistory) {
        /**
         * 진행중인지 확인
         */
        if (!groupUpdateHistory.getStatus().equals(EGroupUpdateFlag.PROGRESS)) {
            return ;
        }

        EGroupUpdateFlag flag = EGroupUpdateFlag.DONE;

        if (groupUpdateHistory.getFlag().equals(EUpdateFlag.DELEGATE)) {

            UserHasGroup targetUser = userHasGroupRepository.getUserHasGroupByUserIdAndGroupIdAndIsMaker(
                    groupUpdateHistory.getTargetId(), groupUpdateHistory.getGroupId(), false);

            /**
             * 위임 진행은, 위임받을 대상자가 같은 사용처 그룹장이 있는지 체크한다
             */
            List<Long> makerCompanyIds = certRepo.getMakersCompanyIds(groupUpdateHistory.getTargetId());
            Long companyId = groupRepo.getCompanyIdByGroupId(groupUpdateHistory.getGroupId());

            if (makerCompanyIds.contains(companyId)) {
                /**
                 * 같은 사용처에 그룹장이 있을경우 실패
                 */
                flag = EGroupUpdateFlag.CANCEL;
            } else {
                /**
                 * 그룹장 위임
                 */
                UserHasGroup makerHasGroup = userHasGroupRepository.getUserHasGroupByUserIdAndGroupIdAndIsMaker(
                        groupUpdateHistory.getMakerId(), groupUpdateHistory.getGroupId(), true);

                makerHasGroup.updatePosition(EGroupPositionFlag.CONSENTER);
                targetUser.updatePosition(EGroupPositionFlag.MAKER);
            }
        } else if (groupUpdateHistory.getFlag().equals(EUpdateFlag.TO_REFERRER)) {

            UserHasGroup targetUser = userHasGroupRepository.getUserHasGroupByUserIdAndGroupIdAndIsMaker(
                    groupUpdateHistory.getTargetId(), groupUpdateHistory.getGroupId(), false);

            /**
             * 참조자로 변경
             */
            targetUser.updatePosition(EGroupPositionFlag.REFERRER);
        } else if (groupUpdateHistory.getFlag().equals(EUpdateFlag.WITHDRAW)) {

            UserHasGroup targetUser = userHasGroupRepository.getUserHasGroupByUserIdAndGroupIdAndIsMaker(
                    groupUpdateHistory.getTargetId(), groupUpdateHistory.getGroupId(), false);

            /**
             * 그룹에서 제거
             */
            User maker = userRepository.getUserById(groupUpdateHistory.getMakerId());
            targetUser.updateStatus(EGroupStatusFlag.WITHDRAW, maker);

            /**
             * 제거된 푸시 날림
             */
            Group groupInfo = groupRepository.findByGroupId(groupUpdateHistory.getGroupId());

            PushDto.PushData newPush = PushDto.PushData.builder()
                    .alarmFlag(EAlarmFlag.GROUP)
                    .targetFlag(ETargetFlag.INVITE_FROM)
                    .targetId(targetUser.getId())
                    .title("그룹 해제")
                    .message(maker.getName() + " 님께서 그룹에서 해제시켰습니다.")
                    .build();

            String content = maker.getName() + " 님께서 <font color='#FF0045'>" + groupInfo.getName() +
                    "</font> 그룹에서 <b><u>해제</u></b>하셨습니다.";

            UserNotification saveUserNotification = UserNotification
                    .builder()
                    .userId(groupUpdateHistory.getTargetId())
                    .alarmFlag(EAlarmFlag.GROUP)
                    .targetFlag(ETargetFlag.INVITE_FROM)
                    .targetId(targetUser.getId())
                    .title(maker.getName() + " 님께서 그룹에서 해제시켰습니다.")
                    .content(content)
                    .build();
            userNotificationRepository.save(saveUserNotification);

            UserGroupHistory history = UserGroupHistory.builder()
                    .userId(groupUpdateHistory.getTargetId())
                    .groupId(groupUpdateHistory.getGroupId())
                    .status(EGroupHistoryFlag.WITHDRAW)
                    .build();
            userGroupHistoryRepository.save(history);

            groupRepo.updateUserHasGroupStatus(targetUser.getId(), EGroupStatusFlag.WITHDRAW, maker);

            List<Long> idsList = new ArrayList<>();
            idsList.add(groupUpdateHistory.getTargetId());
            snsService.sendPush(new Gson().toJson(newPush), idsList);

        } else {
            User maker = userRepository.getUserById(groupUpdateHistory.getMakerId());
            /**
             * 해당 그룹 아이디에 속한 사용자들 해제 처리
             */
            groupRepo.updateUserHasGroupStatusIds(groupUpdateHistory.getGroupId(), EGroupStatusFlag.WITHDRAW, maker);
            userGroupHistoryRepository.insertHistoryFromGroup(EGroupStatusFlag.WITHDRAW.getCode(), groupUpdateHistory.getGroupId());

            /**
             * 초대 했었던 sns 기록 지우기
             */
            groupRepo.deleteUserGroupTempByGroupId(groupUpdateHistory.getGroupId());

            /**
             * 그룹 is_active = false 처리
             */
            Group group = groupRepository.findByGroupId(groupUpdateHistory.getGroupId());
            group.delete();
        }
        groupUpdateHistory.updateStatus(flag);
    }

    private void sendCallbackPOST(String callbackUrl, String stateCode, String code, String time) {

        String result;
        try {
            URL url = new URL(callbackUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");

            JSONObject body = new JSONObject();
            body.put("state", stateCode);
            body.put("code", code);
            body.put("platform", "SERVER");
            body.put("type", time);

            OutputStreamWriter wr = new OutputStreamWriter(conn.getOutputStream(), "UTF-8");
            wr.write(body.toString());
            wr.flush();

            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                log.error("callback error ::: " + conn.getResponseCode());
                return;
            }

            BufferedReader br = new BufferedReader(new InputStreamReader((conn.getInputStream())));

            String output;
            while ((output = br.readLine()) != null) {
                result = output;
            }
            conn.disconnect();

        } catch (Exception e) {
            log.error("callback exception ::: " + e.getMessage());
            return;
        }
    }

    @Transactional
    public CertResponse.CertList getCertGroupList(long groupId, Pageable pageable) {

        /**
         * 인증되지 않은 리스트는 상단에,
         * 인증된 리스트는 최근순
         */
        List<CertDto.Cert> certList = certRepo.getCertGroupListByGroupId(SecurityUtil.getCurrentUserUuid(), groupId, pageable);
        return new CertResponse.CertList(certList);
    }

    private boolean ipMathes(String ip, String subnet) {
        IpAddressMatcher ipAddressMatcher = new IpAddressMatcher(subnet);
        return ipAddressMatcher.matches(ip);
    }

    public String getLocation(String ip) {

        String [] ipArr = ip.split("\\.");

        String location = Constants.LOCATION_DEFAULT;
        if (ipArr.length < 4) {
            return location;
        }

        try {
            String network = ipArr[0] + "." + ipArr[1] + ".";
            GeoIpBlock findBlock = null;

            List<GeoIpBlock> ipBlocks = certRepo.getIpBlocks(network);

            for (GeoIpBlock ipBlock : ipBlocks) {
                if (ipMathes(ip, ipBlock.getNetwork())) {
                    findBlock = ipBlock;
                    break;
                }
            }

            if (findBlock != null) {
                String addr = certRepo.getAddressByGeonameId(findBlock.getGeoname_id());
                location = Constants.LOCATION_KOREA_SPACE + addr;
            }
        } catch (Exception e) {
            return location;
        }

        return location;
    }

    public void checkDelegate(Long userId, Long companyId) {
        /**
         * 생성한 그룹의 사용자가 위임받을 사용처의 그룹인지 확인
         * 맞으면 진행중인 인증은 거절 처리
         */
        GroupUpdateHistory groupUpdateHistory = groupRepo.getCheckDelegateCompany(userId, companyId);
        if (groupUpdateHistory != null) {
            /**
             * 위임받을 사용처가 있고, 진행중이면 해당 인증건에 대해서 거절 처리
             */
            GroupCert cert = groupCertRepository.getById(groupUpdateHistory.getGroupCertId());
            GroupCertHistory userCertHistory = groupCertHistoryRepository.getGroupCertHistoryByUserIdAndCertId(userId, groupUpdateHistory.getGroupCertId());

            userCertHistory.updateCancel();
            cert.updateCertAuth(ECertFlag.REJECT);
            groupUpdateHistory.updateStatus(EGroupUpdateFlag.CANCEL);
        }

    }

    @Transactional(readOnly = true)
    public CertResponse.ProgressCert getProgressCertCount(long groupId) {
        User userInfo = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());
        /**
         * 해당 그룹에서 진행중인 인증 건수
         */
        int progressCount = 0;
        Long count = certRepo.getProgressGroupCert(userInfo.getId(), groupId);
        if (count != null) {
            progressCount = count.intValue();
        }

        return new CertResponse.ProgressCert(progressCount);
    }
}

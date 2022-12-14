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
         * ???????????? ?????? ???????????? ?????????,
         * ????????? ???????????? ????????? ???
         */
        List<CertDto.Cert> certList = certRepo.getCertList(SecurityUtil.getCurrentUserUuid(), type, pageable);
        return new CertResponse.CertList(certList);
    }

    @Transactional
    public TSIDServerResponse<CertResponse.CertDetail> getCertDetail(...) {
        /**
         * ?????? ?????? ??????
         * ?????? ????????? ?????????????????? ??????
         * ?????? ?????? ?????? ??????
         */
        if (!token.getStatus().equals(ETokenStatusFlag.ACTIVE)) {

            String message = "?????? ???????????? ?????????????????????.\n?????? ?????????????????????.\n";

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
            throw new TSIDServerException(ErrorCode.CERT_NOT_EXIST, "?????????????????? ???????????????.");
        }

        /**
         * ?????? ?????? ?????? ?????? ?????? ??????
         */
        userNotificationRepository.updateUserNotificationByCertIdAndUserId(certId, userInfo.getId());

        UserHasGroup groupUser = userHasGroupRepository.getUserHasGroupByCertIdAndUserId(certId, userInfo.getId());
        if (groupUser == null) {
            ErrorResponse error = new ErrorResponse();
            error.setCode(ErrorCode.CERT_NOT_EXIST.getCode());
            error.setMessage("????????? ??? ?????? ????????? ?????????.");
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
            message = "?????? ????????? <font color='#FF0045'>??????</font> ???????????????.";
        } else if (certDetail.getStatus().equals(ECertFlag.EXPIRED)) {
            isExpire = true;
            message = "????????? ?????? ?????????\n" +
                    "<font color='#FF0045'>??????</font> ???????????????.";
        } else if (certDetail.getStatus().equals(ECertFlag.REJECT)) {
            message = "?????? ????????? <font color='#FF0045'>??????</font> ???????????????.";
        } else {
            if (ZonedDateTime.now().isAfter(certDetail.getExpireDate())) {
                isExpire = true;
                certDetail.setStatus(ECertFlag.EXPIRED);
                message = "????????? ?????? ?????????\n" +
                        "<font color='#FF0045'>??????</font> ???????????????.";

                groupCertRepository.updateGroupCertExpiredByGroupCertId(certDetail.getId());

                /**
                 * ?????? ?????? ?????? ???????????? ????????? ????????? ??????
                 */
                groupRepo.updateGroupUpdateHistoryExpired(certDetail.getId());
            } else {
                message = "????????? <font color='#FF0045'>?????????</font> ?????????.";
                if (certDetail.getIsGroup()) {
                    message = "?????? " + message;
                }
                GroupCertHistory groupCertHistory = certDetail.getGroupCertHistory();
                if (groupCertHistory.getStatus().equals(ECertHistoryFlag.BEFORE)) {
                    if (isMaker) {
                        message = "?????? <font color='#FF0045'>??????</font>??? ????????????.";
                    }
                } else {
                    /**
                     * ????????? ?????? ??????
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
             * ????????? ???????????? ??????
             * ?????? ????????? ?????? ?????? ??????
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
                        userCertHistory.setUserTel(EncryptUtil.aes256Encrypt(decKey, "????????? ??????"));
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
             * ?????? / ?????? ??? ?????? ??????
             * ?????? : ???????????? ?????? (????????????)
             * ?????? : ???*??? ?????? / ???*??? ??? 2??? ??????
             */
            if (certDetail.getStatus().equals(ECertFlag.EXPIRED)) {
                int timer = certHistoryList.size() * 180;
                failMessage = "???????????? ?????? (" + timer + "???)";
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
                    failMessage += " ??? " + rejectCnt + "???";
                }
                failMessage += " ??????";
            }

            if (!certDetail.getStatus().equals(ECertFlag.PROGRESS)) {
                certHistoryList = new ArrayList<>();
            }

            makerName = CommonUtil.nameConvertMasking(certDetail.getMakerName());
            makerTel = EncryptUtil.aes256Encrypt(decKey, certDetail.getMakerTel());
        }

        /**
         * TODO: ????????? ?????? ???????????? ????????? ?????????
         */
        String tempLocation = "????????????";

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
                certName = "?????? ??????";
                break;
            case WITHDRAW:
                certName = "?????? ??????";
                break;
            case TO_REFERRER:
                certName = "?????? ??????";
                break;
        }
        return certName;
    }

    @Transactional
    public CertResponse.CertAuthenticate certAuth(...) {
        /**
         * ????????????
         * ????????? ?????? ??????
         * 1. ????????? ????????? ????????????
         * 2. ?????? ??? ?????? ????????????
         * 3. ?????? ????????? ????????????
         * 4. ?????? ???????????? ???????????????
         * 5. ?????? ????????? ?????? ???????????? ????????? ??? ????????? ?????????
         */
        User userInfo = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        UserAccessToken userToken = userAccessTokenRepository.findUserAccessTokenByUserIdAndDeviceKey(userInfo.getId(), request.getDeviceKey());
        if (userToken == null) {
            throw new TSIDServerException(ErrorCode.CERT_ALREADY_DONE, "???????????? ?????? ???????????????.");
        }

        UserRandomKey keyInfo = userRandomKeyRepository.getUserRandomKeyByUserIdAndKey(userInfo.getId(), request.getAuthenticateKey());
        if (keyInfo == null) {
            throw new TSIDServerException(ErrorCode.CERT_AUTH_REJECT, "???????????? ?????? ???????????????.");
        }

        GroupCert cert = groupCertRepository.getGroupCertByCertId(certId);
        if (cert == null) {
            throw new TSIDServerException(ErrorCode.CERT_NOT_EXIST, "???????????? ?????? ???????????????.");
        }
        if (cert.getIsCert()) {
            throw new TSIDServerException(ErrorCode.CERT_ALREADY_DONE, "????????? ?????? ?????????????????????.");
        }

        GroupCertHistory certHistories = groupCertHistoryRepository.getGroupCertHistoryByUserIdAndCertId(userInfo.getId(), certId);
        if (certHistories == null) {
            throw new TSIDServerException(ErrorCode.CERT_ALREADY_DONE, "?????? ????????? ????????????.");
        }
        if (!certHistories.getStatus().equals(ECertHistoryFlag.BEFORE)) {
            throw new TSIDServerException(ErrorCode.CERT_ALREADY_DONE, "?????? ????????? ???????????????.");
        }
        if (certHistories.getPosition().equals(EGroupPositionFlag.REFERRER)) {
            throw new TSIDServerException(ErrorCode.CERT_REFERRER_CANT, "???????????? ????????? ??? ??? ????????????.");
        }

        /**
         * ????????????, ????????????
         * ?????? ??????????????? ???????????????
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
             * ????????? ???????????? ??????
             * ?????? ????????? ???????????? ????????? ????????? ???????????? ????????????
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
             * ?????? ??????
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
            // ?????? ??????
            userService.insertUserActionLog(userInfo.getId(), EActionFlag.CERT, certId);
        } else {
            // ?????? ??????
            userService.insertUserActionLog(userInfo.getId(), EActionFlag.CERT_REFUSE, certId);
        }

        /**
         * ??????????????? ??????
         * ??????????????? ???????????? ??????
         */
        CertDto.CertSns certSns = certRepo.getCertSnsInfoByCertId(certId);
        Long makerId = certSns.getUserId();

        if (makerId.equals(userInfo.getId())) {
            cert.updateCertLocation(ipAddress, location, device);
        }

        CertResponse.CertAuthenticate response = CertResponse.CertAuthenticate.builder().build();
        if (isDone) {
            String title = "?????? ??????";
            String content = certSns.getName() + " ?????? ????????? ????????? ?????????????????????.";
            String alarmContent = "<font color='#3881ED'><b>" + certSns.getName() + "</b></font> ?????? ????????? ????????? <b><u>??????</b></u>???????????????.";
            if (!isCert) {
                title = "?????? ??????";
                content = certSns.getName() + " ?????? ????????? ????????? ?????????????????????.";
                alarmContent = "<font color='#3881ED'><b>" + certSns.getName() + "</b></font> ?????? ????????? ????????? <b><u>??????</b></u>???????????????.";
            }

            String roleCode = certRepo.getCertRoleCodeByCertId(certId);

            if (roleCode.equals(Constants.GROUP_DELEGATE_GRANT_TYPE) ||
                    roleCode.equals(Constants.GROUP_TO_REFERRER_GRANT_TYPE) ||
                    roleCode.equals(Constants.GROUP_WITHDRAW_GRANT_TYPE) ||
                    roleCode.equals(Constants.GROUP_DESTROY_GRANT_TYPE)) {

                /**
                 * ???????????? ????????? ?????? ????????? ????????? ??????
                 * ?????? ?????? ????????? ?????? ??????
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
                     * ?????? ??? ????????????
                     * callback url ??? POST ??????
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
                 * ?????? ??????????????? ?????? ?????????
                 * ?????? ????????????
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
             * ??????????????? ?????? ?????????
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

        String content = " ?????? ????????? ";

        switch (flag) {
            case DELEGATE:
                content += "????????? ????????? ";
                break;
            case TO_REFERRER:
                content += "???????????? ????????? ";
                break;
            case DESTROY:
                content += "?????? ????????? ";
                break;
            case WITHDRAW:
                content += "????????? ????????? ";
                break;
        }

        String result;
        if (status.equals(EGroupUpdateFlag.DONE)) {
            result = "??????";
        } else {
            result = "??????";
        }
        if (isHtml) {
            result = "<b><u>" + result + "</u></b>";
        }
        return content + result + "???????????????.";
    }

    private String groupUpdateTitle(EUpdateFlag flag, EGroupUpdateFlag status) {

        String title = "";
        switch (flag) {
            case DELEGATE:
                title = "????????? ?????? ";
                break;
            case TO_REFERRER:
                title = "???????????? ?????? ";
                break;
            case DESTROY:
                title = "?????? ?????? ";
                break;
            case WITHDRAW:
                title = "????????? ?????? ";
                break;
        }

        if (status.equals(EGroupUpdateFlag.DONE)) {
            title += "??????";
        } else {
            title += "??????";
        }
        return title;
    }

    private void makeGroupUpdate(GroupUpdateHistory groupUpdateHistory) {
        /**
         * ??????????????? ??????
         */
        if (!groupUpdateHistory.getStatus().equals(EGroupUpdateFlag.PROGRESS)) {
            return ;
        }

        EGroupUpdateFlag flag = EGroupUpdateFlag.DONE;

        if (groupUpdateHistory.getFlag().equals(EUpdateFlag.DELEGATE)) {

            UserHasGroup targetUser = userHasGroupRepository.getUserHasGroupByUserIdAndGroupIdAndIsMaker(
                    groupUpdateHistory.getTargetId(), groupUpdateHistory.getGroupId(), false);

            /**
             * ?????? ?????????, ???????????? ???????????? ?????? ????????? ???????????? ????????? ????????????
             */
            List<Long> makerCompanyIds = certRepo.getMakersCompanyIds(groupUpdateHistory.getTargetId());
            Long companyId = groupRepo.getCompanyIdByGroupId(groupUpdateHistory.getGroupId());

            if (makerCompanyIds.contains(companyId)) {
                /**
                 * ?????? ???????????? ???????????? ???????????? ??????
                 */
                flag = EGroupUpdateFlag.CANCEL;
            } else {
                /**
                 * ????????? ??????
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
             * ???????????? ??????
             */
            targetUser.updatePosition(EGroupPositionFlag.REFERRER);
        } else if (groupUpdateHistory.getFlag().equals(EUpdateFlag.WITHDRAW)) {

            UserHasGroup targetUser = userHasGroupRepository.getUserHasGroupByUserIdAndGroupIdAndIsMaker(
                    groupUpdateHistory.getTargetId(), groupUpdateHistory.getGroupId(), false);

            /**
             * ???????????? ??????
             */
            User maker = userRepository.getUserById(groupUpdateHistory.getMakerId());
            targetUser.updateStatus(EGroupStatusFlag.WITHDRAW, maker);

            /**
             * ????????? ?????? ??????
             */
            Group groupInfo = groupRepository.findByGroupId(groupUpdateHistory.getGroupId());

            PushDto.PushData newPush = PushDto.PushData.builder()
                    .alarmFlag(EAlarmFlag.GROUP)
                    .targetFlag(ETargetFlag.INVITE_FROM)
                    .targetId(targetUser.getId())
                    .title("?????? ??????")
                    .message(maker.getName() + " ????????? ???????????? ?????????????????????.")
                    .build();

            String content = maker.getName() + " ????????? <font color='#FF0045'>" + groupInfo.getName() +
                    "</font> ???????????? <b><u>??????</u></b>???????????????.";

            UserNotification saveUserNotification = UserNotification
                    .builder()
                    .userId(groupUpdateHistory.getTargetId())
                    .alarmFlag(EAlarmFlag.GROUP)
                    .targetFlag(ETargetFlag.INVITE_FROM)
                    .targetId(targetUser.getId())
                    .title(maker.getName() + " ????????? ???????????? ?????????????????????.")
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
             * ?????? ?????? ???????????? ?????? ???????????? ?????? ??????
             */
            groupRepo.updateUserHasGroupStatusIds(groupUpdateHistory.getGroupId(), EGroupStatusFlag.WITHDRAW, maker);
            userGroupHistoryRepository.insertHistoryFromGroup(EGroupStatusFlag.WITHDRAW.getCode(), groupUpdateHistory.getGroupId());

            /**
             * ?????? ????????? sns ?????? ?????????
             */
            groupRepo.deleteUserGroupTempByGroupId(groupUpdateHistory.getGroupId());

            /**
             * ?????? is_active = false ??????
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
         * ???????????? ?????? ???????????? ?????????,
         * ????????? ???????????? ?????????
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
         * ????????? ????????? ???????????? ???????????? ???????????? ???????????? ??????
         * ????????? ???????????? ????????? ?????? ??????
         */
        GroupUpdateHistory groupUpdateHistory = groupRepo.getCheckDelegateCompany(userId, companyId);
        if (groupUpdateHistory != null) {
            /**
             * ???????????? ???????????? ??????, ??????????????? ?????? ???????????? ????????? ?????? ??????
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
         * ?????? ???????????? ???????????? ?????? ??????
         */
        int progressCount = 0;
        Long count = certRepo.getProgressGroupCert(userInfo.getId(), groupId);
        if (count != null) {
            progressCount = count.intValue();
        }

        return new CertResponse.ProgressCert(progressCount);
    }
}

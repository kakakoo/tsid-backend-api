package com.tsid.api.service;

import com.google.gson.Gson;
import com.tsid.api.exception.ErrorCode;
import com.tsid.api.exception.TSIDServerException;
import com.tsid.api.repo.CertRepo;
import com.tsid.api.repo.CompanyRepo;
import com.tsid.api.repo.GroupRepo;
import com.tsid.api.repo.UserRepo;
import com.tsid.api.util.*;
import com.tsid.domain.entity.certRole.CertRole;
import com.tsid.domain.entity.company.Company;
import com.tsid.domain.entity.company.CompanyRepository;
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
import com.tsid.domain.entity.groupUpdateHistory.GroupUpdateHistory;
import com.tsid.domain.entity.groupUpdateHistory.GroupUpdateHistoryRepository;
import com.tsid.domain.entity.permission.Permission;
import com.tsid.domain.entity.permission.PermissionRepository;
import com.tsid.domain.entity.user.User;
import com.tsid.domain.entity.user.UserRepository;
import com.tsid.domain.entity.userGroupHistory.UserGroupHistory;
import com.tsid.domain.entity.userGroupHistory.UserGroupHistoryRepository;
import com.tsid.domain.entity.userGroupTemp.UserGroupTemp;
import com.tsid.domain.entity.userGroupTemp.UserGroupTempRepository;
import com.tsid.domain.entity.userHasGroup.UserHasGroup;
import com.tsid.domain.entity.userHasGroup.UserHasGroupRepository;
import com.tsid.domain.entity.userNotification.UserNotification;
import com.tsid.domain.entity.userNotification.UserNotificationRepository;
import com.tsid.domain.entity.userSmsHistory.UserSmsHistory;
import com.tsid.domain.entity.userSmsHistory.UserSmsHistoryRepository;
import com.tsid.domain.enums.*;
import com.tsid.domain.enums.group.EGroupHistoryFlag;
import com.tsid.domain.enums.group.EGroupPositionFlag;
import com.tsid.domain.enums.group.EGroupStatusFlag;
import com.tsid.domain.enums.group.EUpdateFlag;
import com.tsid.domain.enums.notification.EAlarmFlag;
import com.tsid.domain.enums.notification.ETargetFlag;
import com.tsid.internal.dto.req.SnsRequest;
import com.tsid.internal.sdk.SnsClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.tsid.api.util.Constants.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepo groupRepo;
    private final UserRepo userRepo;
    private final CompanyRepo companyRepo;
    private final CertRepo certRepo;

    private final GroupRepository groupRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final UserSmsHistoryRepository userSmsHistoryRepository;
    private final PermissionRepository permissionRepository;
    private final UserHasGroupRepository userHasGroupRepository;
    private final UserGroupTempRepository userGroupTempRepository;
    private final GroupHasCompanyRepository groupHasCompanyRepository;
    private final UserNotificationRepository userNotificationRepository;
    private final UserGroupHistoryRepository userGroupHistoryRepository;
    private final GroupHasPermissionRepository groupHasPermissionRepository;
    private final GroupCertRepository groupCertRepository;
    private final GroupCertHistoryRepository groupCertHistoryRepository;
    private final GroupUpdateHistoryRepository groupUpdateHistoryRepository;

    private final SnsClient snsService;
    private final CertService certService;
    private final UserService userService;

    @Transactional
    public GroupResponse.GroupCert deleteGroup(...){
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        UserHasGroup makerGroup = groupRepo.getMakerUserHasGroupByGroupIdAndUserId(id, resultUser.getId());

        if(makerGroup == null)
            throw new TSIDServerException(ErrorCode.REMOVE_GROUP, EErrorActionType.NONE, "???????????? ????????? ???????????? ?????? ?????? ????????? ?????? ??????????????????.");

        /**
         * ?????? ????????? ?????? ??????
         */
        List<UserHasGroup> groupUsers = groupRepo.getActiveUserGroupByGroupId(id);
        if (groupUsers.size() > 1) {
            Company company = companyRepo.getCompanyByGroupId(id);
            CertRole role = certRepo.getCertRoleByCode(Constants.GROUP_DESTROY_GRANT_TYPE);
            Group groupInfo = groupRepository.findByGroupId(id);
            Long certId = makeGroupUpdateCert(...);

            return new GroupResponse.GroupCert(certId);
        }

        /**
         * ?????? ?????? ???????????? ?????? ???????????? ?????? ??????
         */
        groupRepo.updateUserHasGroupStatusIds(id, EGroupStatusFlag.WITHDRAW, resultUser);
        userGroupHistoryRepository.insertHistoryFromGroup(EGroupStatusFlag.WITHDRAW.getCode(), id);

        /**
         * ?????? ????????? sns ?????? ?????????
         */
        groupRepo.deleteUserGroupTempByGroupId(id);

        /**
         * ?????? is_active = false ??????
         */
        Group group = groupRepository.findByGroupId(id);
        group.delete();
        return new GroupResponse.GroupCert(0L);
    }

    @Transactional(readOnly = true)
    public GroupResponse.GroupUserInfo getGroupUserInfo(...){

        GroupDto.GroupUserInfo resultGroupUser = groupRepo.getUserGroupInfo(id, status);

        if(resultGroupUser==null)
            throw new TSIDServerException(ErrorCode.GET_GROUP_USER_INFO, EErrorActionType.NONE, "?????? ????????? ???????????????.");

        UserHasGroup makerHasGroup = groupRepo.getMakerGroupByGroupId(resultGroupUser.getGroupId());

        String groupMakerName = "????????? ?????????";
        if (makerHasGroup != null) {
            groupMakerName = makerHasGroup.getUser().getName();
        }

        resultGroupUser.setGroupMakerName(groupMakerName);

        return new GroupResponse.GroupUserInfo(resultGroupUser);
    }

    @Transactional
    public void unGroupUser(...){
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        UserHasGroup resultUserGroup = groupRepo.getUserHasGroupByUserIdAndGroupId(resultUser.getId(), id);

        if(resultUserGroup==null)
            throw new TSIDServerException(ErrorCode.UN_GROUP, EErrorActionType.NONE, "???????????? ?????? ????????? ????????????.");

        UserHasGroup makerGroup = groupRepo.getMakerGroupByGroupId(id);

        if(makerGroup.getUser().getId().equals(resultUser.getId()))
            throw new TSIDServerException(ErrorCode.UN_GROUP, EErrorActionType.NONE, "??????????????? ?????? ????????? ????????? ????????????.");

        String title = "?????? ?????? ??????";
        String message = resultUser.getName() + "????????? " + resultUserGroup.getGroup().getName() + " ?????? ????????? ?????????????????????.";
        PushDto.PushData newPush = PushDto.PushData.builder()
                .build();

        String content = resultUser.getName() + " ????????? <font color='#FF0045'>" + resultUserGroup.getGroup().getName() +
                "</font> ????????? <b><u>??????</u></b>??? ?????????????????????.";

        UserNotification saveUserNotification = UserNotification
                .builder()
                .build();

        List<Long> idsList = new ArrayList<>();
        idsList.add(makerGroup.getUser().getId());

        snsService.sendPush(new Gson().toJson(newPush), idsList);

        if(!resultUserGroup.getStatus().equals(EGroupStatusFlag.RELEASE)){
            resultUserGroup.updateStatus(EGroupStatusFlag.RELEASE, resultUser);
            userNotificationRepository.save(saveUserNotification);
        }

        UserGroupHistory history = UserGroupHistory.builder()
                .build();
        userGroupHistoryRepository.save(history);
    }

    @Transactional(readOnly = true)
    public GroupResponse.GroupDetail getGroupDetailByAlarm(...) {

        Long groupId = groupRepo.getGroupIdByUserHasGroupsId(userHasGroupId);
        return getGroupDetail(...);
    }

    @Transactional(readOnly = true)
    public GroupResponse.GroupDetail getGroupDetail(...){
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        Boolean isSign = false;
        Boolean isMaker = false;

        List<GroupDto.GroupInvite> resultList = groupRepo.getGroupUserList(id, resultUser.getId());

        int consenter = 0;
        int referrer = 0;
        //????????? ???????????? ????????? isSign true
        for(GroupDto.GroupInvite groupInvite: resultList){
            if (groupInvite.getPosition().equals(EGroupPositionFlag.MAKER)) {
                if (groupInvite.getUserId().equals(resultUser.getId())) {
                    isMaker = true;
                }
                groupInvite.setIsMaker(true);
            } else {
                groupInvite.setIsMaker(false);
            }
            if(groupInvite.getUserId().equals(resultUser.getId()))
                isSign = true;

            if (encKey != null) {
                String encTel = "???????????? ?????????";
                groupInvite.setTel(encTel);
            }
            groupInvite.setName(CommonUtil.nameConvertMasking(groupInvite.getName()));

            if (groupInvite.getPosition().equals(EGroupPositionFlag.REFERRER)) {
                referrer++;
            } else if (groupInvite.getPosition().equals(EGroupPositionFlag.CONSENTER)) {
                consenter++;
            }
        }

        if(!isSign)
            throw new TSIDServerException(ErrorCode.GET_GROUP_DETAIL, EErrorActionType.NONE, "???????????? ?????? ????????? ????????????.");

        GroupDto.Group group = groupRepo.getCustomGroupById(id);

        group.setUserCount(consenter + referrer + 1);   // ???????????? ????????? ??????
        group.setConsenter(consenter);
        group.setReferrer(referrer);

        CompanyDto.CompanyInfo resultCompany = companyRepo.getCustomCompanyByGroupId(id);

        return new GroupResponse.GroupDetail(resultList, resultCompany, group, isMaker);
    }

    @Transactional
    public void updateInviteGroup(...){
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        UserHasGroup resultUserGroup = groupRepo.getUserHasGroupByUserIdAndGroupId(resultUser.getId(), id, EGroupStatusFlag.INVITE);

        if(resultUserGroup == null)
            throw new TSIDServerException(ErrorCode.UPDATE_INVITE_GROUP, EErrorActionType.NONE, "???????????? ?????? ????????? ????????????.");

        UserHasGroup makerGroup = groupRepo.getMakerGroupByGroupId(id);

        PushDto.PushData newPush = PushDto.PushData.builder()
                .build();

        /**
         * ????????? ?????? ???????????? ??????
         */
        UserNotification saveUserNotification = UserNotification
                .builder()
                .build();

        String content = resultUser.getName() + " ????????? <font color='#FF0045'>" + resultUserGroup.getGroup().getName() +
                "</font> ?????? ????????? ";

        UserGroupHistory history;
        if (request.getStatus().equals(EParamStatusType.REJECT)) {
            newPush.setTitle("?????? ?????? ??????");
            newPush.setMessage(resultUser.getName() + " ????????? " + NoticeMessage.GROUP_USER_REFUSE_INVITE);

            saveUserNotification.setTitle(ALARM_TITLE_GROUP_WITHDRAW);
            saveUserNotification.setContent(content + "<b><u>??????</u></b>???????????????.");

            history = UserGroupHistory.builder()
                    .build();

            groupRepo.updateUserHasGroupStatus(resultUserGroup.getId(), EGroupStatusFlag.CANCEL, resultUser);

        } else {
            newPush.setTitle("?????? ?????? ??????");
            newPush.setMessage(resultUser.getName() + " ????????? " + NoticeMessage.GROUP_USER_APPROVE_INVITE);
            saveUserNotification.setTitle(ALARM_TITLE_GROUP_INVITE);
            saveUserNotification.setContent(content + "<b><u>??????</u></b>???????????????.");

            resultUserGroup.updateGroupJoin();

            history = UserGroupHistory.builder()
                    .build();
        }

        userGroupHistoryRepository.save(history);

        List<Long> idsList = new ArrayList<>();
        idsList.add(makerGroup.getUser().getId());

        snsService.sendPush(new Gson().toJson(newPush), idsList);
        userNotificationRepository.save(saveUserNotification);
    }

    @Transactional
    public void updateRemoveGroup(...){
        if(request.getStatus().equals(EParamStatusType.ACCEPT) || request.getStatus().equals(EParamStatusType.REJECT)){

            User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

            UserHasGroup makerGroup = groupRepo.getMakerUserHasGroupByGroupIdAndUserId(id, resultUser.getId());

            if(makerGroup == null)
                throw new TSIDServerException(ErrorCode.UPDATE_REMOVE_GROUP, EErrorActionType.NONE, "???????????? ????????? ???????????? ?????? ?????? ????????? ?????? ??????????????????.");

            UserHasGroup resultUserGroup = groupRepo.getUserHasGroupByUserIdAndGroupId(userId, id);

            if(resultUserGroup == null)
                throw new TSIDServerException(ErrorCode.UPDATE_REMOVE_GROUP, EErrorActionType.NONE, "???????????? ?????? ????????? ????????????.");

            if(!resultUserGroup.getStatus().equals(EGroupStatusFlag.RELEASE))
                throw new TSIDServerException(ErrorCode.UPDATE_REMOVE_GROUP, EErrorActionType.NONE, "?????? ????????? ????????? ??????????????????..");

            PushDto.PushData newPush = PushDto.PushData.builder()
                    .build();

            UserNotification saveUserNotification = UserNotification
                    .builder()
                    .build();

            String content = resultUser.getName() + " ????????? <font color='#FF0045'>" + resultUserGroup.getGroup().getName()
                    + "</font> ?????? ????????? ";

            UserGroupHistory history;
            if (request.getStatus().equals(EParamStatusType.REJECT)) {
                newPush.setTitle("?????? ?????? ??????");
                newPush.setMessage(resultUser.getName() + "????????? ?????? ?????? ????????? ?????????????????????.");
                saveUserNotification.setContent(content + "<b><u>??????</u></b>???????????????.");
                resultUserGroup.updateStatus(EGroupStatusFlag.ACTIVE, resultUser);

                history = UserGroupHistory.builder()
                        .build();
            } else {
                newPush.setTitle("?????? ?????? ??????");
                newPush.setMessage(resultUser.getName() + "????????? ?????? ?????? ????????? ?????????????????????.");
                saveUserNotification.setContent(content + "<b><u>??????</u></b>???????????????.");

                groupRepo.updateUserHasGroupStatus(resultUserGroup.getId(), EGroupStatusFlag.WITHDRAW, resultUser);

                history = UserGroupHistory.builder()
                        .build();
            }
            userGroupHistoryRepository.save(history);

            /**
             * ???????????? ?????????
             */
            List<Long> idsList = new ArrayList<>();
            idsList.add(userId);

            snsService.sendPush(new Gson().toJson(newPush), idsList);
            userNotificationRepository.save(saveUserNotification);
        } else {
            throw new TSIDServerException(ErrorCode.UPDATE_REMOVE_GROUP, EErrorActionType.NONE, "??????/????????? ???????????????.");
        }
    }


    @Transactional
    public void updateGroup(Long id, GroupRequest.GroupUpdate request){
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        UserHasGroup makerGroup = groupRepo.getMakerUserHasGroupByGroupIdAndUserId(id, resultUser.getId());

        if(makerGroup == null)
            throw new TSIDServerException(ErrorCode.UPDATE_GROUP, EErrorActionType.NONE, "???????????? ????????? ???????????? ?????? ?????? ????????? ?????? ??????????????????.");

        Group resultGroup = makerGroup.getGroup();

        //?????? ????????? ????????? ????????????
        Company companyInfo = companyRepo.getCompanyByGroupId(id);

        //?????? ????????? ????????? id??? ????????? id??? ???????????? ?????????????????? ??????
        if(!companyInfo.getId().equals(request.getCompanyId())){

            if (resultGroup.getIsAuto()) {
                throw new TSIDServerException(ErrorCode.UPDATE_GROUP, EErrorActionType.NONE, "??????????????? ???????????? ????????? ??? ????????????.");
            }

            List<Long> resultCompanyList = companyRepo.getCompanyIdsWhereUserMakersGroup(resultUser.getId());
            //??????????????? ????????? ID??? ????????? ???????????? ?????? ????????? ID??? ????????? ??????
            if (resultCompanyList.contains(request.getCompanyId())) {
                throw new TSIDServerException(ErrorCode.UPDATE_GROUP, EErrorActionType.NONE, "????????? ????????? ????????? ID: " + request.getCompanyId() +" ?????????.");
            }

            if(request.getCompanyId() != null){
                Company resultCompany = companyRepo.getActiveCompany(request.getCompanyId());
                if (resultCompany == null) {
                    throw new TSIDServerException(ErrorCode.UPDATE_GROUP, EErrorActionType.NONE, "???????????? ?????? ????????? ID: " + request.getCompanyId() + " ?????????.");
                }
                companyRepo.deleteCompanyGroup(companyInfo.getId(), id);

                GroupHasCompany saveGroupHasCompany = GroupHasCompany
                        .builder()
                        .build();
                groupHasCompanyRepository.save(saveGroupHasCompany);
            }
        }
    }

    @Transactional(readOnly = true)
    public GroupResponse.GroupList getGroupList(...){

        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        List<GroupDto.Group> result = groupRepo.getGroupListByGroupIds(resultUser.getId(), pageable);

        if(result.isEmpty())
            return new GroupResponse.GroupList(new ArrayList<>());

        /**
         * ???????????? ???????????? ??????.
         * ???????????? ????????? ???????????????
         * ??????????????? ????????? ??????
         */
        for (GroupDto.Group group : result) {
            group.setMakerName(CommonUtil.nameConvertMasking(group.getMakerName()));
            group.setUserCount(group.getConsenter() + group.getReferrer() + 1);
        }

        return new GroupResponse.GroupList(result);
    }

    @Transactional(readOnly = true)
    public GroupResponse.InviteUserGroup getInviteUserList(...){
        /**
         * ????????? ????????? ?????????
         */
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        /**
         * ?????? ????????? ????????? ?????????
         */
        List<GroupDto.InviteTargetUserData> inviteUserGroups = groupRepo.getInviteUserList(resultUser.getId(), pageable);

        List<GroupDto.InviteUserGroupTo> resultList = new ArrayList<>();
        for (GroupDto.InviteTargetUserData inviteUser : inviteUserGroups) {

            String targetName = CommonUtil.nameConvertMasking(inviteUser.getActionName());
            EStatusFlag actionStatus = inviteUser.getActionStatus();
            if (!actionStatus.equals(EStatusFlag.ACTIVE)) {
                targetName = "***";
            }

            GroupDto.InviteUserGroupTo groupTo = GroupDto.InviteUserGroupTo.builder()
                    .build();
            resultList.add(groupTo);
        }

        if (pageable.getOffset() == 0) {
            List<GroupDto.TempUserInfo> userInfoList = groupRepo.getInviteTempUserList(resultUser.getId());

            if(!userInfoList.isEmpty()){
                String targetName = CommonUtil.nameConvertMasking(resultUser.getName());
                for (GroupDto.TempUserInfo tempUserInfo : userInfoList) {

                    String tempTel = "???????????? ?????????";

                    GroupDto.InviteUserGroupTo groupTo = GroupDto.InviteUserGroupTo.builder()
                            .build();
                    resultList.add(0, groupTo);
                }
            }
        }

        return new GroupResponse.InviteUserGroup(resultList);
    }

    @Transactional(readOnly = true)
    public GroupResponse.InviteUserGroup getInvitedGroupList(...){
        /**
         * ??? ????????? ?????? ?????????
         * ?????? ???????????? ?????? ?????? ????????? ????????????
         * ?????? ????????? ?????? ??? ????????? ?????? ????????????
         */
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        /**
         * ??? ????????? ????????? ?????? ????????????
         */
        List<GroupDto.InvitedUserData> userHasGroups = groupRepo.getInvitedMeWithPaging(resultUser.getId(), pageable);

        /**
         * ??? ????????? ????????? ?????? ?????? ????????????
         */
        List<GroupDto.InviteUserGroupTo> resultList = new ArrayList<>();

        for (GroupDto.InvitedUserData invitedUserData : userHasGroups) {

            String userName = CommonUtil.nameConvertMasking(invitedUserData.getInviteName());
            String userTel = "???????????? ?????????";
            EStatusFlag inviteStatus = invitedUserData.getInviteStatus();
            if (!inviteStatus.equals(EStatusFlag.ACTIVE)) {
                userName = "***";
            }

            String targetName = CommonUtil.nameConvertMasking(invitedUserData.getActionName());
            EStatusFlag actionStatus = invitedUserData.getActionStatus();
            if (!actionStatus.equals(EStatusFlag.ACTIVE)) {
                targetName = "***";
            }

            GroupDto.InviteUserGroupTo data = GroupDto.InviteUserGroupTo.builder()
                    .build();
            resultList.add(data);
        }

        return new GroupResponse.InviteUserGroup(resultList);
    }

    @Transactional(readOnly = true)
    public GroupResponse.GroupInviteUser getGroupInviteList(...){

        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        List<GroupDto.GroupInviteUser> resultUserGroupList = groupRepo.getGroupList(resultUser.getId(), pageable);

        if(resultUserGroupList.isEmpty())
            return new GroupResponse.GroupInviteUser(new ArrayList<>());

        //group maker ?????? ??????
        for (GroupDto.GroupInviteUser groupInviteUser : resultUserGroupList) {
            groupInviteUser.setUserName(CommonUtil.nameConvertMasking(groupInviteUser.getUserName()));
            groupInviteUser.setTel("???????????? ?????????");
        }

        return new GroupResponse.GroupInviteUser(resultUserGroupList);
    }

    @Transactional
    public GroupResponse.GroupCert deleteGroupUser(...){
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        UserHasGroup makerGroup = groupRepo.getMakerUserHasGroupByGroupIdAndUserId(groupId, resultUser.getId());

        if(makerGroup == null)
            throw new TSIDServerException(ErrorCode.DELETE_GROUP_USER, EErrorActionType.NONE, "???????????? ????????? ???????????? ?????? ?????? ????????? ?????? ??????????????????.");

        PushDto.PushData newPush = PushDto.PushData.builder()
                .build();

        /**
         * ????????? ?????? ???????????? ??????
         */
        UserNotification saveUserNotification = UserNotification
                .builder()
                .build();

        String content = resultUser.getName() + " ????????? <font color='#FF0045'>" + makerGroup.getGroup().getName() +
                "</font> ??????";

        if(request.getStatus().equals(EParamStatusType.REMOVE)){

            Long userHasGroupId = groupRepo.getUserHasGroupId(userId, groupId, EGroupStatusFlag.ACTIVE);
            if(userHasGroupId == null){
                throw new TSIDServerException(ErrorCode.INVITE_DELETE_USER_GROUP, EErrorActionType.NONE, "?????? ????????? ????????? ??????????????????.");
            }

            newPush.setTitle("?????? ??????");
            newPush.setMessage(resultUser.getName() + " ????????? ???????????? ?????????????????????.");
            saveUserNotification.setContent(content + "?????? <b><u>??????</u></b>???????????????.");
            saveUserNotification.setTargetFlag(ETargetFlag.REMOVE);

            UserGroupHistory history = UserGroupHistory.builder()
                    .build();
            userGroupHistoryRepository.save(history);

            groupRepo.updateUserHasGroupStatus(userHasGroupId, EGroupStatusFlag.WITHDRAW, resultUser);

        } else if(request.getStatus().equals(EParamStatusType.CANCEL)){

            Long userHasGroupId = groupRepo.getUserHasGroupId(userId, groupId, EGroupStatusFlag.INVITE);
            if(userHasGroupId == null){
                throw new TSIDServerException(ErrorCode.INVITE_DELETE_USER_GROUP, EErrorActionType.NONE, "?????? ????????? ????????? ??????????????????.");
            }
            newPush.setTitle("?????? ??????");
            newPush.setMessage(resultUser.getName() + " ????????? ????????? ?????????????????????.");
            saveUserNotification.setContent(content + " ????????? <b><u>??????</u></b>???????????????.");
            saveUserNotification.setTargetFlag(ETargetFlag.INVITE_FROM);

            UserGroupHistory history = UserGroupHistory.builder()
                    .build();
            userGroupHistoryRepository.save(history);

            groupRepo.updateUserHasGroupStatus(userHasGroupId, EGroupStatusFlag.CANCEL, resultUser);

        } else {
            throw new TSIDServerException(ErrorCode.DELETE_GROUP_USER, EErrorActionType.NONE, "??????/????????? ???????????????.");
        }

        List<Long> idsList = new ArrayList<>();
        idsList.add(userId);

        snsService.sendPush(new Gson().toJson(newPush), idsList);
        userNotificationRepository.save(saveUserNotification);
        return new GroupResponse.GroupCert(0L);
    }

    @Transactional
    public void invite(...){

        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());
        UserHasGroup makerGroup = groupRepo.getMakerUserHasGroupByGroupIdAndUserId(groupId, resultUser.getId());

        if(makerGroup == null)
            throw new TSIDServerException(ErrorCode.INVITE_USER_GROUP, EErrorActionType.NONE, "???????????? ????????? ???????????? ?????? ?????? ????????? ?????? ??????????????????.");

        //????????? ???????????? ???????????? ??????
        if(request.getUserId()!=null){

            User inviteUser = userRepository.findByIdAndStatus(request.getUserId(), EStatusFlag.ACTIVE)
                    .orElseThrow(() -> new TSIDServerException(ErrorCode.INVITE_USER_GROUP, EErrorActionType.NONE, "????????? ??? ?????? ??????????????????"));

            Long userExist = groupRepo.getUserInGroupExist(groupId, request.getUserId());
            if (userExist != null) {
                throw new TSIDServerException(ErrorCode.INVITE_USER_GROUP, EErrorActionType.NONE, "?????? ????????? ??????????????????.");
            }

            Group inviteGroup = makerGroup.getGroup();

            UserHasGroup saveUserHasGroup = UserHasGroup.builder()
                    .build();
            userHasGroupRepository.save(saveUserHasGroup);

            String title = "?????? ??????";
            String message = makerGroup.getUser().getName() + " ????????? " + inviteGroup.getName() + " ????????? ?????????????????????.";
            PushDto.PushData newPush = PushDto.PushData.builder()
                    .build();

            List<Long> idsList = new ArrayList<>();
            idsList.add(request.getUserId());

            snsService.sendPush(new Gson().toJson(newPush), idsList);

            String content = makerGroup.getUser().getName() + " ????????? <font color='#FF0045'>" + inviteGroup.getName() + "</font> ????????? <b><u>??????</u></b>???????????????.";

            UserNotification saveUserNotification = UserNotification
                    .builder()
                    .build();
            userNotificationRepository.save(saveUserNotification);

            UserGroupHistory history = UserGroupHistory.builder()
                    .build();
            userGroupHistoryRepository.save(history);
        }

        // TSID ????????? ???????????? ????????? ??????????????? ?????????
        if(request.getUserId() == null && request.getTel() != null){
            User existUserByTel = userRepository.getExistUserByTel(request.getTel());

            if(existUserByTel != null)
                throw new TSIDServerException(ErrorCode.INVITE_USER_GROUP, EErrorActionType.NONE, "?????? ???????????? ?????? ?????????????????????.");

            List<Long> userSmsHistory = userRepo.getUserSmsHistory(request.getTel());

            if(userSmsHistory.size() < Constants.DAILY_SEND_INVITE_MESSAGE_COUNT){

                String content = userRepo.getInstallMessage();
                SnsRequest.SmsCustom smsRequest = SnsRequest.SmsCustom
                        .build();
                snsService.sendSmsCustom(smsRequest);

                UserSmsHistory saveUserSmsHistory = UserSmsHistory
                        .builder()
                        .groupId(groupId)
                        .tel(request.getTel())
                        .build();
                userSmsHistoryRepository.save(saveUserSmsHistory);

                Optional<UserGroupTemp> resultUserGroupTemp = userGroupTempRepository.findByGroupIdAndTelAndStatus(groupId, request.getTel(), EGroupStatusFlag.WAIT);

                if(resultUserGroupTemp.isEmpty()){
                    UserGroupTemp saveUserGroupTemp = UserGroupTemp.builder()
                            .build();

                    userGroupTempRepository.save(saveUserGroupTemp);
                }

            } else {
                throw new TSIDServerException(ErrorCode.INVITE_USER_GROUP, EErrorActionType.NONE, "?????? ?????? ?????? ????????? ????????????(20)??? ?????????????????????.");
            }

        }

        // ?????? ??????
        userService.insertUserActionLog(resultUser.getId(), EActionFlag.GROUP_INVITE, groupId);

    }

    @Transactional
    public void create(...){

        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        //????????? ????????? ????????? ????????? ???????????? ????????????.;
        List<Long> resultCompanyList = companyRepo.getCompanyIdsByGroupIds(resultUser.getId());

        // ????????? ????????? ???????????? ?????????
        if (resultCompanyList.contains(request.getCompanyId())) {
            throw new TSIDServerException(ErrorCode.CREATE_GROUP, EErrorActionType.NONE, "?????? ????????? ???????????? ID: " + request.getCompanyId() + "??? ????????????.");
        }

        Group saveGroup = Group.builder()
                .build();
        groupRepository.save(saveGroup);

        UserHasGroup saveUserHasGroup = UserHasGroup
                .builder()
                .build();
        userHasGroupRepository.save(saveUserHasGroup);

        // ?????? ??????
        userService.insertUserActionLog(resultUser.getId(), EActionFlag.GROUP_MAKE, saveGroup.getId());

        Company resultCompany = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new TSIDServerException(ErrorCode.CREATE_GROUP, EErrorActionType.NONE, "???????????? ?????? ????????? ID: " + request.getCompanyId() + " ?????????."));

        GroupHasCompany saveGroupHasCompany = GroupHasCompany
                .builder()
                .build();
        groupHasCompanyRepository.save(saveGroupHasCompany);

        Permission resultPermission = permissionRepository.findByCode(EPermissionCode.CERT_CONSENT)
                .orElseThrow(() -> new TSIDServerException(ErrorCode.CREATE_GROUP, EErrorActionType.NONE, "???????????? ?????? ?????? ID: " + request.getCompanyId() + " ?????????."));

        //?????? ?????? ????????? 1??? ??????
        GroupHasPermission saveGroupHasPermission = GroupHasPermission.builder()
                .build();
        groupHasPermissionRepository.save(saveGroupHasPermission);

        UserGroupHistory history = UserGroupHistory.builder()
                .build();
        userGroupHistoryRepository.save(history);

        /**
         * ????????? ????????????, ???????????? ??????????????? ??????
         */
        certService.checkDelegate(resultUser.getId(), resultCompany.getId());
    }

    @Transactional
    public GroupResponse.GroupCert updateGroupRole(...) {
        /**
         * ?????? ?????? ??????
         * ????????? ?????????, ???????????? ?????? ???????????? ???????????? ??????????????? ??????
         */
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        UserHasGroup makerGroup = groupRepo.getMakerUserHasGroupByGroupIdAndUserId(id, resultUser.getId());

        if(makerGroup == null)
            throw new TSIDServerException(ErrorCode.UPDATE_GROUP_ROLE, EErrorActionType.NONE, "???????????? ????????? ???????????? ?????? ?????? ????????? ?????? ??????????????????.");

        Long certId = 0L;
        if (request.getPosition().equals(EGroupPositionFlag.MAKER)) {
            /**
             * ??????????????? ??????
             * ????????? ???????????? ?????? ???????????? ????????? ????????? ????????? ??????
             */
            Company company = companyRepo.getCompanyByGroupId(id);

            if (request.getPosition().equals(EGroupPositionFlag.MAKER)) {
                List<UserHasGroup> hasGroups = groupRepo.getMakerGroupsByCompanyAndUser(company.getId(), request.getUserId());
                if (hasGroups != null && hasGroups.size() > 0) {
                    throw new TSIDServerException(ErrorCode.UPDATE_GROUP_ROLE_MAKER, EErrorActionType.NONE, "?????? ????????? ????????? ????????? ????????? ????????????.");
                }
            }
            Group groupInfo = makerGroup.getGroup();
            CertRole role;
            if (request.getPosition().equals(EGroupPositionFlag.MAKER)) {
                role = certRepo.getCertRoleByCode(Constants.GROUP_DELEGATE_GRANT_TYPE);
            } else {
                role = certRepo.getCertRoleByCode(Constants.GROUP_TO_REFERRER_GRANT_TYPE);
            }
            certId = makeGroupUpdateCert(...);

        } else {
            UserHasGroup groupInfo = groupRepo.getUserHasGroupByUserIdAndGroupId(request.getUserId(), id);
            groupInfo.updatePosition(request.getPosition());
        }
        return new GroupResponse.GroupCert(certId);
    }

    private Long makeGroupUpdateCert(...) {

        /**
         * ???????????? ?????? ????????? ????????? ??????
         */
        GroupUpdateHistory updateHistory = groupRepo.checkGroupUpdateHistory(groupInfo.getId(), role.getCode());
        if (updateHistory != null) {
            throw new TSIDServerException(ErrorCode.UPDATE_GROUP_ROLE, EErrorActionType.NONE, "???????????? ?????? ????????????.");
        }

        /**
         * ?????? ?????? ??????
         */
        CertMemberDto certMemberDto = certRepo.getGroupCertMember(groupInfo.getId());

        /**
         * ?????? ??????
         */
        List<GroupCert> certList = certRepo.getCertListByGroupId(groupInfo.getId());
        int certCount = 1;
        if (certList != null) {
            certCount = certList.size() + 1;
        }

        HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String ipAddress = IpUtil.getIpAddress(req);
        String location = certService.getLocation(ipAddress);

        GroupCert groupCert = GroupCert.builder()
                .build();
        groupCertRepository.save(groupCert);

        GroupUpdateHistory groupUpdateHistory = GroupUpdateHistory.builder()
                .build();
        groupUpdateHistoryRepository.save(groupUpdateHistory);

        List<UserHasGroup> activeGroupUser = groupRepo.getActiveUserGroupByGroupId(groupInfo.getId());

        for (UserHasGroup userHasGroup : activeGroupUser) {
            GroupCertHistory groupCertHistory = GroupCertHistory.builder()
                    .build();
            groupCertHistoryRepository.save(groupCertHistory);
        }

        List<Long> userIds = activeGroupUser.stream()
                .map(p -> p.getUser().getId()).collect(Collectors.toList());

        /**
         * ????????? ?????? ???????????? ??????
         */
        ETargetFlag targetFlag = ETargetFlag.CERT;

        String content = "<font color='#3881ED'><b>" + groupInfo.getName() + "<b></font>" +
                " ?????? <b><u>????????????</u></b>??? ????????????.";
        for (Long ids : userIds) {
            UserNotification noti = UserNotification.builder()
                    .build();
            userNotificationRepository.save(noti);
        }

        userService.insertUserActionLog(userId, EActionFlag.CERT_MAKE, groupCert.getId());

        /**
         * ?????? ??????????????? push ?????????
         */
        String pushTitle = "???????????? : " + role.getName();
        String pushMessage = groupInfo.getName() + " ?????? " + role.getName() + " ????????? ????????????!";

        PushDto.PushData payload = PushDto.PushData.builder()
                .build();

        snsService.sendPush(new Gson().toJson(payload), userIds);
        return groupCert.getId();
    }

    @Transactional(readOnly = true)
    public GroupDto.InviteUserGroupTo getInviteDetail(...) {

        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        if (type.equals(EInviteType.to)) {
            /**
             * ?????? ????????? ??????
             */
            GroupDto.InviteTargetUserData inviteUserById = groupRepo.getInviteUserById(resultUser.getId(), targetId);

            String targetName = CommonUtil.nameConvertMasking(inviteUserById.getActionName());
            EStatusFlag actionStatus = inviteUserById.getActionStatus();
            if (!actionStatus.equals(EStatusFlag.ACTIVE)) {
                targetName = "***";
            }

            return GroupDto.InviteUserGroupTo.builder()
                    .build();

        } else {
            /**
             * ?????? ???????????? ??????
             */
            GroupDto.InvitedUserData invitedMeWithById = groupRepo.getInvitedMeWithById(resultUser.getId(), targetId);

            String userName = CommonUtil.nameConvertMasking(invitedMeWithById.getInviteName());
            String userTel = "???????????? ?????????";
            EStatusFlag inviteStatus = invitedMeWithById.getInviteStatus();
            if (!inviteStatus.equals(EStatusFlag.ACTIVE)) {
                userName = "***";
            }

            String targetName = CommonUtil.nameConvertMasking(invitedMeWithById.getActionName());
            EStatusFlag actionStatus = invitedMeWithById.getActionStatus();
            if (!actionStatus.equals(EStatusFlag.ACTIVE)) {
                targetName = "***";
            }

            return GroupDto.InviteUserGroupTo.builder()
                    .build();
        }
    }

    @Transactional(readOnly = true)
    public GroupResponse.GroupAlarmCount getGroupAlarmCount() {
        /**
         * ????????? ????????? ?????? ??????, ?????? ????????? ?????? ??????
         */
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        int resultCount = groupRepo.getGroupAlarmCount(resultUser.getId());

        return new GroupResponse.GroupAlarmCount(resultCount);
    }

}

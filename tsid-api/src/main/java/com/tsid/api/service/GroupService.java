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
            throw new TSIDServerException(ErrorCode.REMOVE_GROUP, EErrorActionType.NONE, "존재하는 그룹이 아니거나 그룹 수정 권한이 없는 사용자입니다.");

        /**
         * 그룹 혼자일 때는 삭제
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
         * 해당 그룹 아이디에 속한 사용자들 해제 처리
         */
        groupRepo.updateUserHasGroupStatusIds(id, EGroupStatusFlag.WITHDRAW, resultUser);
        userGroupHistoryRepository.insertHistoryFromGroup(EGroupStatusFlag.WITHDRAW.getCode(), id);

        /**
         * 초대 했었던 sns 기록 지우기
         */
        groupRepo.deleteUserGroupTempByGroupId(id);

        /**
         * 그룹 is_active = false 처리
         */
        Group group = groupRepository.findByGroupId(id);
        group.delete();
        return new GroupResponse.GroupCert(0L);
    }

    @Transactional(readOnly = true)
    public GroupResponse.GroupUserInfo getGroupUserInfo(...){

        GroupDto.GroupUserInfo resultGroupUser = groupRepo.getUserGroupInfo(id, status);

        if(resultGroupUser==null)
            throw new TSIDServerException(ErrorCode.GET_GROUP_USER_INFO, EErrorActionType.NONE, "이미 처리된 정보입니다.");

        UserHasGroup makerHasGroup = groupRepo.getMakerGroupByGroupId(resultGroupUser.getGroupId());

        String groupMakerName = "삭제된 사용자";
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
            throw new TSIDServerException(ErrorCode.UN_GROUP, EErrorActionType.NONE, "가입되어 있는 그룹이 아닙니다.");

        UserHasGroup makerGroup = groupRepo.getMakerGroupByGroupId(id);

        if(makerGroup.getUser().getId().equals(resultUser.getId()))
            throw new TSIDServerException(ErrorCode.UN_GROUP, EErrorActionType.NONE, "자신에게는 해제 요청을 보낼수 없습니다.");

        String title = "그룹 해제 요청";
        String message = resultUser.getName() + "님께서 " + resultUserGroup.getGroup().getName() + " 그룹 해제를 요청하셨습니다.";
        PushDto.PushData newPush = PushDto.PushData.builder()
                .build();

        String content = resultUser.getName() + " 님께서 <font color='#FF0045'>" + resultUserGroup.getGroup().getName() +
                "</font> 그룹에 <b><u>해제</u></b>를 요청하셨습니다.";

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
        //그룹에 가입되어 있으면 isSign true
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
                String encTel = "전화번호 암호화";
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
            throw new TSIDServerException(ErrorCode.GET_GROUP_DETAIL, EErrorActionType.NONE, "가입되어 있는 그룹이 아닙니다.");

        GroupDto.Group group = groupRepo.getCustomGroupById(id);

        group.setUserCount(consenter + referrer + 1);   // 그룹원에 그룹장 추가
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
            throw new TSIDServerException(ErrorCode.UPDATE_INVITE_GROUP, EErrorActionType.NONE, "가입되어 있는 그룹이 아닙니다.");

        UserHasGroup makerGroup = groupRepo.getMakerGroupByGroupId(id);

        PushDto.PushData newPush = PushDto.PushData.builder()
                .build();

        /**
         * 사용처 초대 내역으로 이동
         */
        UserNotification saveUserNotification = UserNotification
                .builder()
                .build();

        String content = resultUser.getName() + " 님께서 <font color='#FF0045'>" + resultUserGroup.getGroup().getName() +
                "</font> 그룹 초대를 ";

        UserGroupHistory history;
        if (request.getStatus().equals(EParamStatusType.REJECT)) {
            newPush.setTitle("그룹 초대 거절");
            newPush.setMessage(resultUser.getName() + " 님께서 " + NoticeMessage.GROUP_USER_REFUSE_INVITE);

            saveUserNotification.setTitle(ALARM_TITLE_GROUP_WITHDRAW);
            saveUserNotification.setContent(content + "<b><u>거절</u></b>하셨습니다.");

            history = UserGroupHistory.builder()
                    .build();

            groupRepo.updateUserHasGroupStatus(resultUserGroup.getId(), EGroupStatusFlag.CANCEL, resultUser);

        } else {
            newPush.setTitle("그룹 초대 승인");
            newPush.setMessage(resultUser.getName() + " 님께서 " + NoticeMessage.GROUP_USER_APPROVE_INVITE);
            saveUserNotification.setTitle(ALARM_TITLE_GROUP_INVITE);
            saveUserNotification.setContent(content + "<b><u>승인</u></b>하셨습니다.");

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
                throw new TSIDServerException(ErrorCode.UPDATE_REMOVE_GROUP, EErrorActionType.NONE, "존재하는 그룹이 아니거나 그룹 해제 권한이 없는 사용자입니다.");

            UserHasGroup resultUserGroup = groupRepo.getUserHasGroupByUserIdAndGroupId(userId, id);

            if(resultUserGroup == null)
                throw new TSIDServerException(ErrorCode.UPDATE_REMOVE_GROUP, EErrorActionType.NONE, "가입되어 있는 그룹이 아닙니다.");

            if(!resultUserGroup.getStatus().equals(EGroupStatusFlag.RELEASE))
                throw new TSIDServerException(ErrorCode.UPDATE_REMOVE_GROUP, EErrorActionType.NONE, "그룹 해제가 불가한 사용자입니다..");

            PushDto.PushData newPush = PushDto.PushData.builder()
                    .build();

            UserNotification saveUserNotification = UserNotification
                    .builder()
                    .build();

            String content = resultUser.getName() + " 님께서 <font color='#FF0045'>" + resultUserGroup.getGroup().getName()
                    + "</font> 그룹 해제를 ";

            UserGroupHistory history;
            if (request.getStatus().equals(EParamStatusType.REJECT)) {
                newPush.setTitle("그룹 해제 거절");
                newPush.setMessage(resultUser.getName() + "님께서 동의 그룹 해제를 거절하였습니다.");
                saveUserNotification.setContent(content + "<b><u>거절</u></b>하셨습니다.");
                resultUserGroup.updateStatus(EGroupStatusFlag.ACTIVE, resultUser);

                history = UserGroupHistory.builder()
                        .build();
            } else {
                newPush.setTitle("그룹 해제 승인");
                newPush.setMessage(resultUser.getName() + "님께서 동의 그룹 해제를 승인하였습니다.");
                saveUserNotification.setContent(content + "<b><u>승인</u></b>하셨습니다.");

                groupRepo.updateUserHasGroupStatus(resultUserGroup.getId(), EGroupStatusFlag.WITHDRAW, resultUser);

                history = UserGroupHistory.builder()
                        .build();
            }
            userGroupHistoryRepository.save(history);

            /**
             * 통보받을 사용자
             */
            List<Long> idsList = new ArrayList<>();
            idsList.add(userId);

            snsService.sendPush(new Gson().toJson(newPush), idsList);
            userNotificationRepository.save(saveUserNotification);
        } else {
            throw new TSIDServerException(ErrorCode.UPDATE_REMOVE_GROUP, EErrorActionType.NONE, "승인/거절만 가능합니다.");
        }
    }


    @Transactional
    public void updateGroup(Long id, GroupRequest.GroupUpdate request){
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        UserHasGroup makerGroup = groupRepo.getMakerUserHasGroupByGroupIdAndUserId(id, resultUser.getId());

        if(makerGroup == null)
            throw new TSIDServerException(ErrorCode.UPDATE_GROUP, EErrorActionType.NONE, "존재하는 그룹이 아니거나 그룹 수정 권한이 없는 사용자입니다.");

        Group resultGroup = makerGroup.getGroup();

        //현재 가입된 사용처 얻어오기
        Company companyInfo = companyRepo.getCompanyByGroupId(id);

        //기존 가입된 사용처 id와 들어온 id가 다르다면 변경된것으로 처리
        if(!companyInfo.getId().equals(request.getCompanyId())){

            if (resultGroup.getIsAuto()) {
                throw new TSIDServerException(ErrorCode.UPDATE_GROUP, EErrorActionType.NONE, "자동생성된 사용처는 변경할 수 없습니다.");
            }

            List<Long> resultCompanyList = companyRepo.getCompanyIdsWhereUserMakersGroup(resultUser.getId());
            //추가하려는 사용처 ID로 기존에 가입되어 있는 사용처 ID가 있는지 확인
            if (resultCompanyList.contains(request.getCompanyId())) {
                throw new TSIDServerException(ErrorCode.UPDATE_GROUP, EErrorActionType.NONE, "기존에 가입된 사용처 ID: " + request.getCompanyId() +" 입니다.");
            }

            if(request.getCompanyId() != null){
                Company resultCompany = companyRepo.getActiveCompany(request.getCompanyId());
                if (resultCompany == null) {
                    throw new TSIDServerException(ErrorCode.UPDATE_GROUP, EErrorActionType.NONE, "존재하지 않는 사용처 ID: " + request.getCompanyId() + " 입니다.");
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
         * 동의자랑 참조자를 더함.
         * 그룹원은 그룹장 포함하지만
         * 동의자에는 그룹장 제외
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
         * 초대한 사용자 리스트
         */
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        /**
         * 내가 초대한 사용자 리스트
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

                    String tempTel = "전화번호 암호화";

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
         * 날 초대한 그룹 리스트
         * 내가 그룹장이 아닌 그룹 리스트 가져오기
         * 해당 그룹의 정보 및 그룹장 정보 가져오기
         */
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        /**
         * 날 초대한 사용자 정보 가져오기
         */
        List<GroupDto.InvitedUserData> userHasGroups = groupRepo.getInvitedMeWithPaging(resultUser.getId(), pageable);

        /**
         * 날 초대한 그룹에 대한 정보 가져오기
         */
        List<GroupDto.InviteUserGroupTo> resultList = new ArrayList<>();

        for (GroupDto.InvitedUserData invitedUserData : userHasGroups) {

            String userName = CommonUtil.nameConvertMasking(invitedUserData.getInviteName());
            String userTel = "전화번호 암호화";
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

        //group maker 정보 입력
        for (GroupDto.GroupInviteUser groupInviteUser : resultUserGroupList) {
            groupInviteUser.setUserName(CommonUtil.nameConvertMasking(groupInviteUser.getUserName()));
            groupInviteUser.setTel("전화번호 암호화");
        }

        return new GroupResponse.GroupInviteUser(resultUserGroupList);
    }

    @Transactional
    public GroupResponse.GroupCert deleteGroupUser(...){
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        UserHasGroup makerGroup = groupRepo.getMakerUserHasGroupByGroupIdAndUserId(groupId, resultUser.getId());

        if(makerGroup == null)
            throw new TSIDServerException(ErrorCode.DELETE_GROUP_USER, EErrorActionType.NONE, "존재하는 그룹이 아니거나 그룹 수정 권한이 없는 사용자입니다.");

        PushDto.PushData newPush = PushDto.PushData.builder()
                .build();

        /**
         * 사용처 초대 내역으로 이동
         */
        UserNotification saveUserNotification = UserNotification
                .builder()
                .build();

        String content = resultUser.getName() + " 님께서 <font color='#FF0045'>" + makerGroup.getGroup().getName() +
                "</font> 그룹";

        if(request.getStatus().equals(EParamStatusType.REMOVE)){

            Long userHasGroupId = groupRepo.getUserHasGroupId(userId, groupId, EGroupStatusFlag.ACTIVE);
            if(userHasGroupId == null){
                throw new TSIDServerException(ErrorCode.INVITE_DELETE_USER_GROUP, EErrorActionType.NONE, "그룹 해제가 불가한 사용자입니다.");
            }

            newPush.setTitle("그룹 해제");
            newPush.setMessage(resultUser.getName() + " 님께서 그룹에서 해제시켰습니다.");
            saveUserNotification.setContent(content + "에서 <b><u>해제</u></b>하셨습니다.");
            saveUserNotification.setTargetFlag(ETargetFlag.REMOVE);

            UserGroupHistory history = UserGroupHistory.builder()
                    .build();
            userGroupHistoryRepository.save(history);

            groupRepo.updateUserHasGroupStatus(userHasGroupId, EGroupStatusFlag.WITHDRAW, resultUser);

        } else if(request.getStatus().equals(EParamStatusType.CANCEL)){

            Long userHasGroupId = groupRepo.getUserHasGroupId(userId, groupId, EGroupStatusFlag.INVITE);
            if(userHasGroupId == null){
                throw new TSIDServerException(ErrorCode.INVITE_DELETE_USER_GROUP, EErrorActionType.NONE, "초대 취소가 불가한 사용자입니다.");
            }
            newPush.setTitle("초대 취소");
            newPush.setMessage(resultUser.getName() + " 님께서 초대를 취소하셨습니다.");
            saveUserNotification.setContent(content + " 초대를 <b><u>취소</u></b>하셨습니다.");
            saveUserNotification.setTargetFlag(ETargetFlag.INVITE_FROM);

            UserGroupHistory history = UserGroupHistory.builder()
                    .build();
            userGroupHistoryRepository.save(history);

            groupRepo.updateUserHasGroupStatus(userHasGroupId, EGroupStatusFlag.CANCEL, resultUser);

        } else {
            throw new TSIDServerException(ErrorCode.DELETE_GROUP_USER, EErrorActionType.NONE, "해제/취소만 가능합니다.");
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
            throw new TSIDServerException(ErrorCode.INVITE_USER_GROUP, EErrorActionType.NONE, "존재하는 그룹이 아니거나 그룹 수정 권한이 없는 사용자입니다.");

        //그룹에 사용자를 초대하는 경우
        if(request.getUserId()!=null){

            User inviteUser = userRepository.findByIdAndStatus(request.getUserId(), EStatusFlag.ACTIVE)
                    .orElseThrow(() -> new TSIDServerException(ErrorCode.INVITE_USER_GROUP, EErrorActionType.NONE, "초대할 수 없는 사용자입니다"));

            Long userExist = groupRepo.getUserInGroupExist(groupId, request.getUserId());
            if (userExist != null) {
                throw new TSIDServerException(ErrorCode.INVITE_USER_GROUP, EErrorActionType.NONE, "이미 초대된 사용자입니다.");
            }

            Group inviteGroup = makerGroup.getGroup();

            UserHasGroup saveUserHasGroup = UserHasGroup.builder()
                    .build();
            userHasGroupRepository.save(saveUserHasGroup);

            String title = "그룹 초대";
            String message = makerGroup.getUser().getName() + " 님께서 " + inviteGroup.getName() + " 그룹에 초대하셨습니다.";
            PushDto.PushData newPush = PushDto.PushData.builder()
                    .build();

            List<Long> idsList = new ArrayList<>();
            idsList.add(request.getUserId());

            snsService.sendPush(new Gson().toJson(newPush), idsList);

            String content = makerGroup.getUser().getName() + " 님께서 <font color='#FF0045'>" + inviteGroup.getName() + "</font> 그룹에 <b><u>초대</u></b>하셨습니다.";

            UserNotification saveUserNotification = UserNotification
                    .builder()
                    .build();
            userNotificationRepository.save(saveUserNotification);

            UserGroupHistory history = UserGroupHistory.builder()
                    .build();
            userGroupHistoryRepository.save(history);
        }

        // TSID 가입한 사용자가 아니면 문자메세지 보내기
        if(request.getUserId() == null && request.getTel() != null){
            User existUserByTel = userRepository.getExistUserByTel(request.getTel());

            if(existUserByTel != null)
                throw new TSIDServerException(ErrorCode.INVITE_USER_GROUP, EErrorActionType.NONE, "이미 가입되어 있는 전화번호입니다.");

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
                throw new TSIDServerException(ErrorCode.INVITE_USER_GROUP, EErrorActionType.NONE, "하루 그룹 초대 메세지 사용건수(20)가 초과되었습니다.");
            }

        }

        // 로그 등록
        userService.insertUserActionLog(resultUser.getId(), EActionFlag.GROUP_INVITE, groupId);

    }

    @Transactional
    public void create(...){

        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        //기존에 생성된 그룹에 사용처 아이디를 불러온다.;
        List<Long> resultCompanyList = companyRepo.getCompanyIdsByGroupIds(resultUser.getId());

        // 생성된 동일한 사용처가 있는지
        if (resultCompanyList.contains(request.getCompanyId())) {
            throw new TSIDServerException(ErrorCode.CREATE_GROUP, EErrorActionType.NONE, "이미 가입된 자동그룹 ID: " + request.getCompanyId() + "가 있습니다.");
        }

        Group saveGroup = Group.builder()
                .build();
        groupRepository.save(saveGroup);

        UserHasGroup saveUserHasGroup = UserHasGroup
                .builder()
                .build();
        userHasGroupRepository.save(saveUserHasGroup);

        // 로그 등록
        userService.insertUserActionLog(resultUser.getId(), EActionFlag.GROUP_MAKE, saveGroup.getId());

        Company resultCompany = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new TSIDServerException(ErrorCode.CREATE_GROUP, EErrorActionType.NONE, "존재하지 않는 사용처 ID: " + request.getCompanyId() + " 입니다."));

        GroupHasCompany saveGroupHasCompany = GroupHasCompany
                .builder()
                .build();
        groupHasCompanyRepository.save(saveGroupHasCompany);

        Permission resultPermission = permissionRepository.findByCode(EPermissionCode.CERT_CONSENT)
                .orElseThrow(() -> new TSIDServerException(ErrorCode.CREATE_GROUP, EErrorActionType.NONE, "존재하지 않는 권한 ID: " + request.getCompanyId() + " 입니다."));

        //처음 그룹 정책은 1인 동의
        GroupHasPermission saveGroupHasPermission = GroupHasPermission.builder()
                .build();
        groupHasPermissionRepository.save(saveGroupHasPermission);

        UserGroupHistory history = UserGroupHistory.builder()
                .build();
        userGroupHistoryRepository.save(history);

        /**
         * 그룹을 생성할때, 위임받을 사용처인지 체크
         */
        certService.checkDelegate(resultUser.getId(), resultCompany.getId());
    }

    @Transactional
    public GroupResponse.GroupCert updateGroupRole(...) {
        /**
         * 그룹 권한 변경
         * 그룹장 위임시, 위임자가 같은 사용처의 그룹장을 하고있으면 안됨
         */
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        UserHasGroup makerGroup = groupRepo.getMakerUserHasGroupByGroupIdAndUserId(id, resultUser.getId());

        if(makerGroup == null)
            throw new TSIDServerException(ErrorCode.UPDATE_GROUP_ROLE, EErrorActionType.NONE, "존재하는 그룹이 아니거나 그룹 수정 권한이 없는 사용자입니다.");

        Long certId = 0L;
        if (request.getPosition().equals(EGroupPositionFlag.MAKER)) {
            /**
             * 그룹장으로 변경
             * 변경할 사용자가 해당 사용처의 그룹장 그룹이 있는지 확인
             */
            Company company = companyRepo.getCompanyByGroupId(id);

            if (request.getPosition().equals(EGroupPositionFlag.MAKER)) {
                List<UserHasGroup> hasGroups = groupRepo.getMakerGroupsByCompanyAndUser(company.getId(), request.getUserId());
                if (hasGroups != null && hasGroups.size() > 0) {
                    throw new TSIDServerException(ErrorCode.UPDATE_GROUP_ROLE_MAKER, EErrorActionType.NONE, "동일 사용처 그룹장 권한을 가지고 있습니다.");
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
         * 진행중인 수정 상태가 있는지 확인
         */
        GroupUpdateHistory updateHistory = groupRepo.checkGroupUpdateHistory(groupInfo.getId(), role.getCode());
        if (updateHistory != null) {
            throw new TSIDServerException(ErrorCode.UPDATE_GROUP_ROLE, EErrorActionType.NONE, "진행중인 건이 있습니다.");
        }

        /**
         * 인증 인원 체크
         */
        CertMemberDto certMemberDto = certRepo.getGroupCertMember(groupInfo.getId());

        /**
         * 인증 횟수
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
         * 그룹장 위임 인증요청 발송
         */
        ETargetFlag targetFlag = ETargetFlag.CERT;

        String content = "<font color='#3881ED'><b>" + groupInfo.getName() + "<b></font>" +
                " 에서 <b><u>인증요청</u></b>이 왔습니다.";
        for (Long ids : userIds) {
            UserNotification noti = UserNotification.builder()
                    .build();
            userNotificationRepository.save(noti);
        }

        userService.insertUserActionLog(userId, EActionFlag.CERT_MAKE, groupCert.getId());

        /**
         * 그룹 사용자에게 push 보내기
         */
        String pushTitle = "인증요청 : " + role.getName();
        String pushMessage = groupInfo.getName() + " 에서 " + role.getName() + " 요청이 왔습니다!";

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
             * 내가 초대한 내역
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
             * 내가 초대받은 내역
             */
            GroupDto.InvitedUserData invitedMeWithById = groupRepo.getInvitedMeWithById(resultUser.getId(), targetId);

            String userName = CommonUtil.nameConvertMasking(invitedMeWithById.getInviteName());
            String userTel = "전화번호 암호화";
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
         * 사용처 그룹에 초대 받기, 해제 요청에 대한 숫자
         */
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        int resultCount = groupRepo.getGroupAlarmCount(resultUser.getId());

        return new GroupResponse.GroupAlarmCount(resultCount);
    }

}

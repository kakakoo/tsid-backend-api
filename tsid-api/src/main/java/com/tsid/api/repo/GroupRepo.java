package com.tsid.api.repo;

import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tsid.domain.entity.group.Group;
import com.tsid.domain.entity.groupUpdateHistory.GroupUpdateHistory;
import com.tsid.domain.entity.user.QUser;
import com.tsid.domain.entity.user.User;
import com.tsid.domain.entity.userHasGroup.QUserHasGroup;
import com.tsid.domain.entity.userHasGroup.UserHasGroup;
import com.tsid.domain.enums.EStatusFlag;
import com.tsid.domain.enums.group.EGroupPositionFlag;
import com.tsid.domain.enums.group.EGroupStatusFlag;
import com.tsid.domain.enums.group.EGroupUpdateFlag;
import com.tsid.domain.enums.group.EUpdateFlag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.querydsl.core.types.ExpressionUtils.count;
import static com.tsid.domain.entity.company.QCompany.company;
import static com.tsid.domain.entity.group.QGroup.group;
import static com.tsid.domain.entity.groupHasCompany.QGroupHasCompany.groupHasCompany;
import static com.tsid.domain.entity.groupUpdateHistory.QGroupUpdateHistory.groupUpdateHistory;
import static com.tsid.domain.entity.user.QUser.user;
import static com.tsid.domain.entity.userGroupTemp.QUserGroupTemp.userGroupTemp;
import static com.tsid.domain.entity.userHasGroup.QUserHasGroup.userHasGroup;

@Component
@RequiredArgsConstructor
public class GroupRepo {

    private final JPAQueryFactory jpaQueryFactory;


    public UserHasGroup getUserHasGroupByUserIdAndGroupId(long userId, long groupId, EGroupStatusFlag flag){
        return jpaQueryFactory
                .selectFrom(userHasGroup)
                .where(userHasGroup.group.id.eq(groupId),
                        userHasGroup.user.id.eq(userId),
                        userHasGroupStatusEq(flag))
                .fetchOne();
    }

    private BooleanExpression userHasGroupStatusEq(EGroupStatusFlag flag){
        if (flag == null) {
            return userHasGroup.status.in(EGroupStatusFlag.RELEASE, EGroupStatusFlag.ACTIVE);
        } else {
            return userHasGroup.status.eq(flag);
        }
    }

    /**
     * List<UserHasGroup>
     *
     */

    public List<UserHasGroup> getMakerGroupsByCompanyAndUser(Long companyId, Long userId){
        return jpaQueryFactory
                .selectFrom(userHasGroup)
                .join(userHasGroup.group, group)
                .join(groupHasCompany).on(group.id.eq(groupHasCompany.group.id),
                        groupHasCompany.company.id.eq(companyId))
                .where(userHasGroup.user.id.eq(userId),
                        userHasGroup.status.eq(EGroupStatusFlag.ACTIVE),
                        userHasGroup.position.eq(EGroupPositionFlag.MAKER))
                .fetch();
    }

    public String getGroupName(Long userId, String name) {

        String groupName = name;
        int index = 1;
        while (true) {
            Long groupCount = jpaQueryFactory
                    .select(count(group))
                    .from(group)
                    .join(userHasGroup).on(group.id.eq(userHasGroup.group.id))
                    .where(group.name.eq(groupName),
                            userHasGroup.user.id.eq(userId))
                    .fetchFirst();
            if (groupCount == null || groupCount == 0) {
                break;
            }
            groupName = name + index;
            index++;
        }

        return groupName;
    }

    public List<GroupDto.InvitedUserData> getInvitedMeWithPaging(Long userId, Pageable pageable) {
        return jpaQueryFactory
                .select(Projections.bean(
                        GroupDto.InvitedUserData.class,
                        userHasGroup.id.as("inviteId"),
                        group.id.as("groupId"),
                        group.name.as("groupName"),
                        userHasGroup.status.as("groupStatus"),
                        userHasGroup.invite.id.as("userId"),
                        userHasGroup.invite.name.as("inviteName"),
                        userHasGroup.invite.tel.as("inviteTel"),
                        userHasGroup.invite.status.as("inviteStatus"),
                        userHasGroup.actionUser.name.as("actionName"),
                        userHasGroup.actionUser.status.as("actionStatus"),
                        userHasGroup.updateDate,
                        userHasGroup.createDate))
                .from(userHasGroup)
                .join(group).on(userHasGroup.group.id.eq(group.id),
                        group.isActive.isTrue())
                .where(userHasGroup.user.id.eq(userId),
                        userHasGroup.invite.id.ne(userId),
                        userHasGroup.position.ne(EGroupPositionFlag.MAKER))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(new CaseBuilder()
                                .when(userHasGroup.status.in(EGroupStatusFlag.INVITE, EGroupStatusFlag.RELEASE)).then(1)
                                .when(userHasGroup.status.eq(EGroupStatusFlag.ACTIVE)).then(2)
                                .otherwise(3).asc(),
                        userHasGroup.updateDate.desc())
                .fetch();
    }

    public List<GroupDto.Group> getGroupListByGroupIds(Long userId, Pageable pageable){

        QUserHasGroup groupUsers = new QUserHasGroup("groupUsers");
        QUserHasGroup makerHasGroup = new QUserHasGroup("makerHasGroup");

        return jpaQueryFactory
                .select(Projections.bean(
                        GroupDto.Group.class,
                        groupHasCompany.group.id,
                        group.name,
                        group.isAuto,
                        company.image,
                        new CaseBuilder().when(makerHasGroup.user.id.eq(userId)).then(true).otherwise(false).as("isMine"),
                        user.name.as("makerName"),
                        ExpressionUtils.as(JPAExpressions
                                .select(count(groupUsers))
                                .from(groupUsers)
                                .where(group.id.eq(groupUsers.group.id),
                                        groupUsers.position.eq(EGroupPositionFlag.CONSENTER),
                                        groupUsers.status.in(EGroupStatusFlag.RELEASE, EGroupStatusFlag.ACTIVE)), "consenter"),
                        ExpressionUtils.as(JPAExpressions
                                .select(count(groupUsers))
                                .from(groupUsers)
                                .where(group.id.eq(groupUsers.group.id),
                                        groupUsers.position.eq(EGroupPositionFlag.REFERRER),
                                        groupUsers.status.in(EGroupStatusFlag.RELEASE, EGroupStatusFlag.ACTIVE)), "referrer"),
                        groupHasCompany.createDate))
                .from(userHasGroup)
                .join(group).on(userHasGroup.group.id.eq(group.id),
                        group.isActive.isTrue())
                .join(groupHasCompany).on(group.id.eq(groupHasCompany.group.id))
                .join(company).on(groupHasCompany.company.id.eq(company.id))
                .join(makerHasGroup).on(group.id.eq(makerHasGroup.group.id),
                        makerHasGroup.position.eq(EGroupPositionFlag.MAKER))
                .join(user).on(makerHasGroup.user.id.eq(user.id))
                .where(userHasGroup.user.id.eq(userId),
                        userHasGroup.status.in(EGroupStatusFlag.RELEASE, EGroupStatusFlag.ACTIVE))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(group.id.desc())
                .fetch();
    }

    public List<GroupDto.GroupInvite> getGroupUserList(Long groupId, Long userId){
        return jpaQueryFactory
                .select(Projections.bean(
                        GroupDto.GroupInvite.class,
                        userHasGroup.user.id.as("userId"),
                        user.name,
                        user.tel,
                        userHasGroup.status,
                        userHasGroup.position))
                .from(userHasGroup)
                .innerJoin(user).on(userHasGroup.user.id.eq(user.id), user.status.eq(EStatusFlag.ACTIVE))
                .where(userHasGroup.group.id.eq(groupId),
                        userHasGroup.status.in(EGroupStatusFlag.ACTIVE, EGroupStatusFlag.RELEASE))
                .orderBy(new CaseBuilder().when(userHasGroup.position.eq(EGroupPositionFlag.MAKER)).then(1)
                        .when(user.id.eq(userId)).then(2)
                        .otherwise(3).asc())
                .fetch();
    }

    public GroupDto.Group getCustomGroupById(Long groupId) {
        return jpaQueryFactory
                .select(Projections.bean(
                        GroupDto.Group.class,
                        group.name,
                        new CaseBuilder().when(groupUpdateHistory.flag.eq(EUpdateFlag.DELEGATE)).then(true).otherwise(false).as("isUpdateDelegate"),
                        new CaseBuilder().when(groupUpdateHistory.flag.eq(EUpdateFlag.DESTROY)).then(true).otherwise(false).as("isUpdateDelete"),
                        group.createDate))
                .from(group)
                .leftJoin(groupUpdateHistory).on(group.id.eq(groupUpdateHistory.groupId),
                        groupUpdateHistory.status.eq(EGroupUpdateFlag.PROGRESS))
                .where(group.id.eq(groupId))
                .fetchFirst();
    }

    public List<GroupDto.InviteTargetUserData> getInviteUserList(Long userId, Pageable pageable) {
        return jpaQueryFactory
                .select(Projections.bean(
                        GroupDto.InviteTargetUserData.class,
                        userHasGroup.id,
                        group.id.as("groupId"),
                        group.name.as("groupName"),
                        userHasGroup.createDate,
                        user.id.as("userId"),
                        user.name.as("userName"),
                        user.tel.as("userTel"),
                        userHasGroup.actionUser.name.as("actionName"),
                        userHasGroup.actionUser.status.as("actionStatus"),
                        userHasGroup.updateDate.as("userStatusDate"),
                        userHasGroup.status))
                .from(userHasGroup)
                .join(userHasGroup.user, user).on(user.status.eq(EStatusFlag.ACTIVE))
                .join(userHasGroup.group, group).on(group.isActive.isTrue())
                .where(userHasGroup.invite.id.eq(userId),
                        userHasGroup.user.id.ne(userId))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(new CaseBuilder()
                            .when(userHasGroup.status.in(EGroupStatusFlag.INVITE, EGroupStatusFlag.RELEASE)).then(1)
                            .when(userHasGroup.status.eq(EGroupStatusFlag.ACTIVE)).then(2)
                            .otherwise(3).asc(),
                        userHasGroup.updateDate.desc())
                .fetch();
    }

    public GroupDto.InviteTargetUserData getInviteUserById(Long userId, Long targetId) {
        return jpaQueryFactory
                .select(Projections.bean(
                        GroupDto.InviteTargetUserData.class,
                        userHasGroup.id,
                        group.id.as("groupId"),
                        group.name.as("groupName"),
                        userHasGroup.createDate,
                        user.id.as("userId"),
                        user.name.as("userName"),
                        user.tel.as("userTel"),
                        userHasGroup.actionUser.name.as("actionName"),
                        userHasGroup.actionUser.status.as("actionStatus"),
                        userHasGroup.updateDate.as("userStatusDate"),
                        userHasGroup.status))
                .from(userHasGroup)
                .join(userHasGroup.user, user).on(user.status.eq(EStatusFlag.ACTIVE))
                .join(userHasGroup.group, group).on(group.isActive.isTrue())
                .where(userHasGroup.id.eq(targetId),
                        userHasGroup.invite.id.eq(userId),
                        userHasGroup.user.id.ne(userId))
                .fetchOne();
    }

    public List<GroupDto.TempUserInfo> getInviteTempUserList(Long userId) {
        return jpaQueryFactory
                .select(Projections.bean(
                        GroupDto.TempUserInfo.class,
                        group.id,
                        group.name,
                        group.createDate.as("groupCreateDate"),
                        userGroupTemp.tel,
                        userGroupTemp.createDate))
                .from(userGroupTemp)
                .join(group).on(userGroupTemp.groupId.eq(group.id),
                        group.isActive.isTrue())
                .where(userGroupTemp.inviteId.eq(userId),
                        userGroupTemp.status.eq(EGroupStatusFlag.WAIT))
                .fetch();
    }

    public List<GroupDto.GroupInviteUser> getGroupList(Long userId, Pageable pageable){

        QUserHasGroup makerHasGroup = new QUserHasGroup("makerHasGroup");
        QUser maker = new QUser("maker");

        return jpaQueryFactory
                .select(Projections.bean(
                        GroupDto.GroupInviteUser.class,
                        group.id,
                        group.name.as("groupName"),
                        user.id.as("userId"),
                        maker.name.as("userName"),
                        maker.tel,
                        userHasGroup.status))
                .from(userHasGroup)
                .join(user).on(userHasGroup.user.id.eq(user.id),
                        user.status.eq(EStatusFlag.ACTIVE))
                .join(group).on(userHasGroup.group.id.eq(group.id),
                        group.isActive.isTrue())
                .join(makerHasGroup).on(group.id.eq(makerHasGroup.group.id),
                        makerHasGroup.status.eq(EGroupStatusFlag.ACTIVE),
                        makerHasGroup.position.eq(EGroupPositionFlag.MAKER))
                .join(maker).on(makerHasGroup.user.id.eq(maker.id))
                .where(userHasGroup.user.id.eq(userId),
                        userHasGroup.position.ne(EGroupPositionFlag.MAKER),
                        userHasGroup.status.ne(EGroupStatusFlag.WAIT))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(new CaseBuilder()
                                .when(userHasGroup.status.eq(EGroupStatusFlag.INVITE)).then(0)
                                .when(userHasGroup.status.eq(EGroupStatusFlag.RELEASE)).then(1)
                                .otherwise(2).asc(),
                        userHasGroup.id.desc())
                .fetch();
    }

    public int getGroupAlarmCount(Long userId) {
        int count = 0;

        Long nomakerCount = jpaQueryFactory
                .select(count(userHasGroup))
                .from(userHasGroup)
                .join(group).on(userHasGroup.group.id.eq(group.id),
                        group.isActive.isTrue())
                .where(userHasGroup.user.id.eq(userId),
                        userHasGroup.position.ne(EGroupPositionFlag.MAKER),
                        userHasGroup.status.in(EGroupStatusFlag.INVITE, EGroupStatusFlag.RELEASE))
                .fetchOne();

        QUserHasGroup makerGroup = new QUserHasGroup("makerGroup");

        Long makerCount = jpaQueryFactory
                .select(count(makerGroup))
                .from(userHasGroup)
                .join(group).on(userHasGroup.group.id.eq(group.id),
                        group.isActive.isTrue())
                .join(makerGroup).on(group.id.eq(makerGroup.group.id),
                        makerGroup.position.ne(EGroupPositionFlag.MAKER),
                        makerGroup.status.in(EGroupStatusFlag.INVITE, EGroupStatusFlag.RELEASE))
                .where(userHasGroup.user.id.eq(userId),
                        userHasGroup.position.eq(EGroupPositionFlag.MAKER))
                .fetchOne();

        if (nomakerCount != null) {
            count += nomakerCount.intValue();
        }
        if (makerCount != null) {
            count += makerCount.intValue();
        }

        return count;
    }

    /**
     * update / delete
     *
     */

    public void updateUserHasGroupStatusIds(Long groupId, EGroupStatusFlag flag, User actionUser){
        jpaQueryFactory
                .update(userHasGroup)
                .set(userHasGroup.status, flag)
                .set(userHasGroup.actionUser, actionUser)
                .where(userHasGroup.group.id.eq(groupId))
                .execute();
    }

    public void updateUserHasGroupStatus(Long id, EGroupStatusFlag flag, User actionUser){
        jpaQueryFactory
                .update(userHasGroup)
                .set(userHasGroup.status, flag)
                .set(userHasGroup.actionUser, actionUser)
                .where(userHasGroup.id.eq(id))
                .execute();
    }

    public void deleteUserGroupTempByGroupId(Long groupId) {
        jpaQueryFactory
                .delete(userGroupTemp)
                .where(userGroupTemp.groupId.eq(groupId))
                .execute();
    }

    public void updateMakerWithdraw(Long userId){
        // 사용자가 MAKER 인 그룹에 속한 사용자들 그룹 탈퇴 처리
        List<Long> groupIds = jpaQueryFactory
                .select(group.id)
                .from(userHasGroup)
                .join(group).on(userHasGroup.group.id.eq(group.id),
                        group.isActive.isTrue())
                .where(userHasGroup.user.id.eq(userId),
                        userHasGroup.position.eq(EGroupPositionFlag.MAKER))
                .fetch();

        if (groupIds != null) {
            jpaQueryFactory
                    .update(userHasGroup)
                    .set(userHasGroup.status, EGroupStatusFlag.WITHDRAW_MAKER)
                    .where(userHasGroup.group.id.in(groupIds),
                            userHasGroup.status.in(EGroupStatusFlag.ACTIVE, EGroupStatusFlag.RELEASE))
                    .execute();

            // 사용자가 MAKER 인 그룹 isActive = false 처리
            jpaQueryFactory
                    .update(group)
                    .set(group.isActive, false)
                    .where(group.id.in(groupIds))
                    .execute();
        }

        // 사용자가 속한 그룹 탈퇴처리
        jpaQueryFactory
                .update(userHasGroup)
                .set(userHasGroup.status, EGroupStatusFlag.WITHDRAW_USER)
                .where(userHasGroup.user.id.eq(userId),
                        userHasGroup.position.ne(EGroupPositionFlag.MAKER),
                        userHasGroup.status.in(EGroupStatusFlag.RELEASE, EGroupStatusFlag.ACTIVE))
                .execute();

    }

    public Group getGroupByCompanyAndUser(Long companyId, Long userId) {
        return jpaQueryFactory
                .select(group)
                .from(userHasGroup)
                .join(group).on(userHasGroup.group.id.eq(group.id),
                        group.isActive.isTrue())
                .join(groupHasCompany).on(group.id.eq(groupHasCompany.group.id),
                        groupHasCompany.company.id.eq(companyId))
                .where(userHasGroup.user.id.eq(userId),
                        userHasGroup.position.eq(EGroupPositionFlag.MAKER))
                .fetchOne();
    }

    public Long getUserCountInGroup(Long groupId) {
        return jpaQueryFactory
                .select(count(userHasGroup))
                .from(userHasGroup)
                .where(userHasGroup.group.id.eq(groupId),
                        userHasGroup.status.in(EGroupStatusFlag.ACTIVE, EGroupStatusFlag.RELEASE))
                .fetchOne();
    }

    public void updateGroupUpdateHistoryExpired(Long certId) {
        jpaQueryFactory
                .update(groupUpdateHistory)
                .set(groupUpdateHistory.status, EGroupUpdateFlag.EXPIRED)
                .where(groupUpdateHistory.groupCertId.eq(certId),
                        groupUpdateHistory.status.eq(EGroupUpdateFlag.PROGRESS))
                .execute();
    }
}

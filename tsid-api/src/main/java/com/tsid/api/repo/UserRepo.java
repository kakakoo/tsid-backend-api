package com.tsid.api.repo;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tsid.domain.entity.user.User;
import com.tsid.domain.entity.userActionLog.UserActionLog;
import com.tsid.domain.entity.userGroupTemp.UserGroupTemp;
import com.tsid.domain.entity.userNotification.UserNotification;
import com.tsid.domain.entity.userPush.UserPush;
import com.tsid.domain.enums.EStatusFlag;
import com.tsid.domain.enums.group.EGroupStatusFlag;
import com.tsid.domain.enums.notification.EAlarmFlag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

import static com.tsid.domain.entity.admin.QAdmin.admin;
import static com.tsid.domain.entity.group.QGroup.group;
import static com.tsid.domain.entity.serverInfo.QServerInfo.serverInfo;
import static com.tsid.domain.entity.user.QUser.user;
import static com.tsid.domain.entity.userAccessToken.QUserAccessToken.userAccessToken;
import static com.tsid.domain.entity.userActionLog.QUserActionLog.userActionLog;
import static com.tsid.domain.entity.userGroupTemp.QUserGroupTemp.userGroupTemp;
import static com.tsid.domain.entity.userHasGroup.QUserHasGroup.userHasGroup;
import static com.tsid.domain.entity.userNotification.QUserNotification.userNotification;
import static com.tsid.domain.entity.userPrivacy.QUserPrivacy.userPrivacy;
import static com.tsid.domain.entity.userPush.QUserPush.userPush;
import static com.tsid.domain.entity.userRefreshToken.QUserRefreshToken.userRefreshToken;
import static com.tsid.domain.entity.userSmsHistory.QUserSmsHistory.userSmsHistory;

@Component
@RequiredArgsConstructor
public class UserRepo {

    private final JPAQueryFactory jpaQueryFactory;

    public List<AlarmDto.AlarmMapper> getUserNotificationList(String uuid, Pageable pageable, EAlarmFlag type){
        return jpaQueryFactory
                .select(Projections.bean(
                        AlarmDto.AlarmMapper.class,
                        userNotification.id,
                        userNotification.title,
                        userNotification.content,
                        userNotification.alarmFlag,
                        userNotification.targetFlag,
                        userNotification.targetId,
                        userNotification.readDate,
                        userNotification.createDate,
                        userHasGroup.group.id.as("groupId")))
                .from(user)
                .innerJoin(userNotification).on(user.id.eq(userNotification.userId))
                .leftJoin(userHasGroup).on(userNotification.targetId.eq(userHasGroup.id),
                        userNotification.alarmFlag.eq(EAlarmFlag.GROUP))
                .where(user.uuid.eq(uuid)
                        .and(eqAlarmFlag(type)))
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(new CaseBuilder().when(userNotification.readDate.isNull()).then(0).otherwise(1).asc(),
                        userNotification.id.desc())
                .fetch();
    }

    private BooleanExpression eqAlarmFlag(EAlarmFlag type) {
        if (type == null || type.equals(EAlarmFlag.ALL)) {
            return null;
        }
        return userNotification.alarmFlag.eq(type);
    }

    public List<UserNotification> getUserNotificationNotReadByUuid(String uuid){
        return jpaQueryFactory
                .select(userNotification)
                .from(user)
                .innerJoin(userNotification).on(user.id.eq(userNotification.userId))
                .where(user.uuid.eq(uuid).and(userNotification.readDate.isNull()))
                .fetch();
    }

    public void updateUserGroupTempRead(Long userId) {
        jpaQueryFactory
                .update(userGroupTemp)
                .set(userGroupTemp.status, EGroupStatusFlag.ACTIVE)
                .where(userGroupTemp.userId.eq(userId))
                .execute();
    }

    public User getUserByFidoKey(String key) {
        return jpaQueryFactory
                .selectFrom(user)
                .where(user.fidoRegisterKey.eq(key))
                .fetchOne();
    }

    public User getUserByCi(String ci){
        return jpaQueryFactory
                .select(user)
                .from(userPrivacy)
                .innerJoin(user).on(userPrivacy.id.eq(user.userPrivacy.id))
                .where(userPrivacy.ci.eq(ci))
                .fetchOne();
    }

    public UserDto.UserFindGroup findUserGroup(String tel, Long groupId) {
        return jpaQueryFactory
                .select(Projections.bean(
                        UserDto.UserFindGroup.class,
                        user.id,
                        user.uuid,
                        user.name,
                        userHasGroup.as("userHasGroup")))
                .from(user)
                .leftJoin(userHasGroup).on(user.id.eq(userHasGroup.user.id),
                        userHasGroup.status.in(EGroupStatusFlag.INVITE, EGroupStatusFlag.ACTIVE, EGroupStatusFlag.RELEASE),
                        userHasGroup.group.id.eq(groupId))
                .where(user.tel.eq(tel),
                        user.status.eq(EStatusFlag.ACTIVE))
                .fetchOne();
    }

    public void deleteUserProcess(Long userId, Long privacyId) {
        jpaQueryFactory
                .delete(userRefreshToken)
                .where(userRefreshToken.id.in(
                        JPAExpressions.select(userAccessToken.refreshToken.id)
                                .from(userAccessToken)
                                .where(userAccessToken.userId.eq(userId))
                ))
                .execute();

        jpaQueryFactory
                .delete(userAccessToken)
                .where(userAccessToken.userId.eq(userId))
                .execute();

        jpaQueryFactory
                .delete(userPush)
                .where(userPush.userId.eq(userId))
                .execute();

        jpaQueryFactory
                .delete(userPrivacy)
                .where(userPrivacy.id.eq(privacyId))
                .execute();

        jpaQueryFactory
                .delete(userNotification)
                .where(userNotification.userId.eq(userId))
                .execute();

        jpaQueryFactory
                .update(admin)
                .set(admin.status, EStatusFlag.DELETE)
                .where(admin.user.id.eq(userId))
                .execute();

    }

    public void updateNotification(List<Long> alarmIds) {
        if (!alarmIds.isEmpty()) {
            jpaQueryFactory
                    .update(userNotification)
                    .set(userNotification.readDate, ZonedDateTime.now())
                    .where(userNotification.id.in(alarmIds))
                    .execute();
        }
    }
}

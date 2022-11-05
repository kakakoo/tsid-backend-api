package com.tsid.api.repo;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tsid.api.util.Constants;
import com.tsid.domain.entity.userClientDetail.UserClientDetail;
import com.tsid.domain.enums.token.ETokenStatusFlag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.tsid.domain.entity.user.QUser.user;
import static com.tsid.domain.entity.userAccessToken.QUserAccessToken.userAccessToken;
import static com.tsid.domain.entity.userClientDetail.QUserClientDetail.userClientDetail;
import static com.tsid.domain.entity.userDevice.QUserDevice.userDevice;

@Component
@RequiredArgsConstructor
public class AuthRepo {

    private final JPAQueryFactory jpaQueryFactory;

    public void updateAccessTokenOther(Long userId, String deviceKey){
        jpaQueryFactory
                .update(userAccessToken)
                .set(userAccessToken.status, ETokenStatusFlag.OTHER)
                .where(userAccessToken.userId.eq(userId),
                        userAccessToken.status.eq(ETokenStatusFlag.ACTIVE),
                        userAccessToken.deviceId.in(
                                JPAExpressions.select(userDevice.id)
                                        .from(userDevice)
                                        .where(userDevice.userId.eq(userId),
                                                userDevice.deviceKey.eq(deviceKey))
                        ))
                .execute();
    }

    public UserDto.UserSubRefresh findUserByToken(String token) {
        return jpaQueryFactory
                .select(Projections.bean(
                        UserDto.UserSubRefresh.class,
                        user.uuid,
                        user.id.as("userId"),
                        userAccessToken.clientId))
                .from(userAccessToken)
                .join(user).on(userAccessToken.userId.eq(user.id))
                .where(userAccessToken.token.eq(token))
                .fetchOne();
    }


}

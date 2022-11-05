package com.tsid.api.repo;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tsid.domain.entity.danal.Danal;
import com.tsid.domain.entity.deviceModel.DeviceModel;
import com.tsid.domain.enums.EClientPlatformType;
import com.tsid.domain.enums.token.ETokenStatusFlag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.tsid.domain.entity.danal.QDanal.danal;
import static com.tsid.domain.entity.deviceModel.QDeviceModel.deviceModel;
import static com.tsid.domain.entity.userAccessToken.QUserAccessToken.userAccessToken;
import static com.tsid.domain.entity.userDevice.QUserDevice.userDevice;
import static com.tsid.domain.entity.userPush.QUserPush.userPush;

@Component
@RequiredArgsConstructor
public class DeviceRepo {

    private final JPAQueryFactory jpaQueryFactory;

    public String getDeviceModelByModel(String model) {
        DeviceModel device = jpaQueryFactory
                .selectFrom(deviceModel)
                .where(deviceModel.model.eq(model))
                .limit(1)
                .fetchOne();

        if (device == null) {
            return model;
        } else {
            String brand = device.getBrand() == null ? "" : device.getBrand();
            String name = device.getName() == null ? "" : " " + device.getName();
            return brand + name;
        }
    }

    public String findOtherLoginDevice(Long userId){
        return jpaQueryFactory
                .select(userDevice.device)
                .from(userAccessToken)
                .join(userDevice).on(userAccessToken.deviceId.eq(userDevice.id))
                .where(userAccessToken.userId.eq(userId),
                        userAccessToken.status.eq(ETokenStatusFlag.ACTIVE))
                .fetchOne();
    }

    public EClientPlatformType getUserPlatform(Long userId, String accessToken) {

        String platform = jpaQueryFactory
                .select(userDevice.platform)
                .from(userAccessToken)
                .join(userDevice).on(userAccessToken.deviceId.eq(userDevice.id))
                .where(userAccessToken.token.eq(accessToken),
                        userAccessToken.status.eq(ETokenStatusFlag.ACTIVE))
                .fetchOne();

        if (platform == null) {
            return jpaQueryFactory
                    .select(userPush.platform)
                    .from(userPush)
                    .where(userPush.userId.eq(userId))
                    .fetchOne();
        } else {
            return EClientPlatformType.valueOf(platform);
        }
    }
}

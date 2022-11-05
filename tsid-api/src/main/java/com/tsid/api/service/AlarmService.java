package com.tsid.api.service;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tsid.api.repo.GroupRepo;
import com.tsid.api.repo.UserRepo;
import com.tsid.api.util.CommonUtil;
import com.tsid.api.util.Constants;
import com.tsid.api.util.EncryptUtil;
import com.tsid.api.util.SecurityUtil;
import com.tsid.domain.entity.user.User;
import com.tsid.domain.entity.user.UserRepository;
import com.tsid.domain.entity.userGroupTemp.UserGroupTemp;
import com.tsid.domain.entity.userNotification.UserNotificationRepository;
import com.tsid.domain.enums.group.EGroupStatusFlag;
import com.tsid.domain.enums.notification.EAlarmFlag;
import com.tsid.domain.enums.notification.ETargetFlag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.tsid.api.util.Constants.*;
import static com.tsid.domain.entity.user.QUser.user;
import static com.tsid.domain.entity.userNotification.QUserNotification.userNotification;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmService {

    private final UserRepo userRepo;
    private final GroupRepo groupRepo;
    private final UserRepository userRepository;
    private final UserNotificationRepository userNotificationRepository;

    private final JPAQueryFactory jpaQueryFactory;

    @Transactional(readOnly = true)
    public AlarmResponse.AlarmExist getAlarmExist(...){

        /**
         * 비회원일 때 초대받은 그룹 있었는지 확인
         * 비회원 초대시 - WAIT
         * 회원가입되고 그룹 연결시 - INVITE
         * 확인하면 - ACTIVE
         */
        ............
    }

    @Transactional(readOnly = true)
    public AlarmResponse.AlarmCount getAlarmCount(...){
        /**
         * 읽지않은 알람 갯수
         */
        User userInfo = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());
        Long alarmCount = userNotificationRepository.getNotificationCountNotRead(userInfo.getId());
        if (alarmCount == null) {
            alarmCount = 0L;
        }
        return new AlarmResponse.AlarmCount(alarmCount.intValue());
    }

    @Transactional
    public AlarmResponse.AlarmList getAlarmList(...){
        /**
         * 사용자 알림 가져와서 화면에 맞춰 수정
         */
        List<AlarmDto.Alarm> resultMapper = userRepo.getUserNotiByUserAndType(SecurityUtil.getCurrentUserUuid(), pageable, type);

        if(!resultMapper.isEmpty()){
            List<Long> alarmIds = new ArrayList<>();
            for (AlarmDto.Alarm alarm : resultMapper) {
                alarm.setAlarmImage(alarmImage(alarm.getTitle()));

                if((alarm.getAlarmFlag().equals(EAlarmFlag.NOTICE) ||
                        alarm.getAlarmFlag().equals(EAlarmFlag.GROUP) ||
                        (alarm.getAlarmFlag().equals(EAlarmFlag.AUTH) && alarm.getTargetFlag().equals(ETargetFlag.NONE) ) && alarm.getReadDate()==null)){

                    alarmIds.add(alarm.getId());
                }
            }
            // 알림 읽음 처리
            userRepo.updateNotification(alarmIds);

            return new AlarmResponse.AlarmList(resultMapper);
        }

        return new AlarmResponse.AlarmList(new ArrayList<>());
    }

    private String alarmImage(String title) {
        String image;

        if (title.equals(ALARM_TITLE_GROUP_WITHDRAW)) {
            image = Constants.ALARM_IMAGE_GROUP_WITHDRAW;
        } else if (title.equals(ALARM_TITLE_GROUP_INVITE)) {
            image = Constants.ALARM_IMAGE_GROUP_INVITE;
        } else if (title.equals(ALARM_TITLE_CERT)) {
            image = Constants.ALARM_IMAGE_CERT;
        } else {
            image = Constants.ALARM_IMAGE_NOTICE;
        }

        return image;
    }
}

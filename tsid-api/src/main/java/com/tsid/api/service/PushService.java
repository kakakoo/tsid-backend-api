package com.tsid.api.service;

import com.tsid.api.repo.UserRepo;
import com.tsid.api.util.SecurityUtil;
import com.tsid.domain.entity.user.User;
import com.tsid.domain.entity.user.UserRepository;
import com.tsid.domain.entity.userPush.UserPush;
import com.tsid.domain.entity.userPush.UserPushRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushService {

    private final UserRepo userRepo;
    private final UserRepository userRepository;
    private final UserPushRepository userPushRepository;

    @Transactional
    public void updatePushKey(PushRequest.PushKey request){
        /**
         * 사용자 fcm key 등록
         */

        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        UserPush resultUserPush = userRepo.getUserPushByUserId(resultUser.getId());

        if(resultUserPush==null){
            UserPush saveUserPush = UserPush.builder()
                    .build();
            userPushRepository.save(saveUserPush);
            return;
        }

        resultUserPush.update(request.getPushKey(), request.getPlatform());
        userPushRepository.save(resultUserPush);

    }
}

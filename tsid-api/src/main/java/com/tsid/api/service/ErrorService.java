package com.tsid.api.service;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tsid.api.util.Constants;
import com.tsid.api.util.SecurityUtil;
import com.tsid.domain.entity.errorLog.ErrorLog;
import com.tsid.domain.entity.errorLog.ErrorLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.tsid.domain.entity.user.QUser.user;

@Service
@Slf4j
@RequiredArgsConstructor
public class ErrorService {

    private final ErrorLogRepository errorLogRepository;
    private final JPAQueryFactory jpaQueryFactory;

    @Transactional
    public void insertErrorLog(String platform, String version, String url, String message){

        Long userId;
        String userUuid = SecurityUtil.getCurrentUserUuid();
        if (userUuid == null || userUuid.equals("anonymousUser")) {
            userId = null;
        } else {
            userId = jpaQueryFactory
                    .select(user.id)
                    .from(user)
                    .where(user.uuid.eq(userUuid))
                    .fetchOne();
        }

        ErrorLog errorLog = ErrorLog.builder()
                .userId(userId)
                .os(platform)
                .version(version)
                .url(url)
                .message(message)
                .server(Constants.SERVER_INFO)
                .build();
        errorLogRepository.save(errorLog);
    }
}

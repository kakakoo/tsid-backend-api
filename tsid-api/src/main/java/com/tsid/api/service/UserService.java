package com.tsid.api.service;

import com.tsid.api.exception.ErrorCode;
import com.tsid.api.exception.TSIDServerException;
import com.tsid.api.repo.UserRepo;
import com.tsid.api.util.*;
import com.tsid.domain.entity.user.User;
import com.tsid.domain.entity.user.UserRepository;
import com.tsid.domain.entity.userActionLog.UserActionLog;
import com.tsid.domain.entity.userActionLog.UserActionLogRepository;
import com.tsid.domain.entity.userPrivacy.UserPrivacy;
import com.tsid.domain.enums.EActionFlag;
import com.tsid.domain.enums.EErrorActionType;
import com.tsid.domain.enums.group.EGroupStatusFlag;
import com.tsid.external.sdk.naver.NaverClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepo userRepo;
    private final UserActionLogRepository userActionLogRepository;
    private final UserRepository userRepository;

    private final NaverClient naverClient;

    private final String STORAGE_ENDPOINT = "https://kr.object.ncloudstorage.com/";
    private final String NAVER_BUCKET = "---";
    private final String DISABLED_FILE = "---";

    @Transactional(readOnly = true)
    public UserResponse.UserDetail getUserDetail(...){
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        if(resultUser == null)
            throw new TSIDServerException(ErrorCode.GET_USER_INFO, EErrorActionType.NONE,  "존재하지 않는 유저입니다.");

        String tel = "전화번호 암호화";

        ZonedDateTime disableDate = resultUser.getUserPrivacy().getUpdateDate();
        //현재는 disabledImage로 판별
        if (resultUser.getUserPrivacy().getDisabledImage() == null) {
            disableDate = null;
        }

        UserDto.UserDetail userDetail = UserDto.UserDetail.builder()
                .build();

        return new UserResponse.UserDetail(userDetail);
    }

    @Transactional
    public void updateAlarm(UserRequest.UserUpdate request){
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        if(resultUser == null)
            throw new TSIDServerException(ErrorCode.UPDATE_USER_PRIVACY, EErrorActionType.TOAST,  "존재하지 않는 유저입니다.");

        resultUser.updateAlert(request.getIsAlert());
    }

    @Transactional
    public void updateUserPrivacy(MultipartFile multipartFile){
        /**
         * 장애인 등록증 업로드
         */
        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        if(resultUser == null)
            throw new TSIDServerException(ErrorCode.UPDATE_USER, EErrorActionType.NONE,  "존재하지 않는 유저입니다.");

        try {
            String format = FilenameUtils.getExtension(multipartFile.getOriginalFilename());
            if (!format.equals("jpg") && !format.equals("png") && !format.equals("jpeg")) {
                throw new TSIDServerException(ErrorCode.IMAGE_FORMAT_ERROR, EErrorActionType.NONE,  "이미지를 업로드 해 주세요.");
            }
            String fileName = randomKey(resultUser.getId()) + "." + format;

            String uploadDir = DISABLED_FILE + File.separator + StringUtils.cleanPath(fileName);
            Path copyOfLocation = Paths.get(uploadDir);
            Files.copy(multipartFile.getInputStream(), copyOfLocation, StandardCopyOption.REPLACE_EXISTING);
            boolean chk = StorageUtil.putObject(NAVER_BUCKET, fileName, uploadDir);

            if (chk) {
                File file = new File(uploadDir);
                if(file.delete()){
                    log.info("file delete success");
                } else {
                    log.error("file delete fail");
                }
                /**
                 * 등록
                 */
                UserPrivacy userPrivacy = resultUser.getUserPrivacy();
                userPrivacy.updateImage(fileName);
            }
        } catch (Exception e) {
            log.info(e.getMessage());
            throw new TSIDServerException(ErrorCode.INTERNAL_SERVER_ERROR, EErrorActionType.NONE,  "이미지가 업로드 중 에러");
        }
    }

    @Transactional
    public UserResponse.UserInfo findUserByTel(...){

        String decTel = "복호화 된 전화번호";

        if(!PatternUtil.validTel(decTel)){
            throw new TSIDServerException(ErrorCode.GET_USER_INFO, EErrorActionType.TOAST,  "전화번호 형식이 맞지 않습니다.");
        }

        UserDto.UserInfo result;
        EFindUserType type = EFindUserType.ACTIVE;

        UserDto.UserFindGroup userGroup = userRepo.findUserGroup(decTel, request.getGroupId());
        if (userGroup == null) {
            throw new TSIDServerException(ErrorCode.FIND_USER, EErrorActionType.NONE, "가입되어 있지 않는 사용자입니다.");
        }
        if (userGroup.getUuid().equals(SecurityUtil.getCurrentUserUuid())) {
            throw new TSIDServerException(ErrorCode.FIND_USER, EErrorActionType.NONE, "자신을 초대할 수 없습니다.");
        }

        if (userGroup.getUserHasGroup() != null) {
            if (userGroup.getUserHasGroup().getStatus().equals(EGroupStatusFlag.INVITE)) {
                type = EFindUserType.INVITING;
            } else if (userGroup.getUserHasGroup().getStatus().equals(EGroupStatusFlag.ACTIVE) ||
                    userGroup.getUserHasGroup().getStatus().equals(EGroupStatusFlag.RELEASE)) {
                type = EFindUserType.INVITED;
            }
        }
        result = UserDto.UserInfo.builder()
                .build();

        return new UserResponse.UserInfo(result);
    }

    public UserResponse.UserDisabled getUserDisabledImage() {

        User resultUser = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());
        if(resultUser == null)
            throw new TSIDServerException(ErrorCode.UPDATE_USER, EErrorActionType.NONE,  "존재하지 않는 유저입니다.");

        String path = resultUser.getUserPrivacy().getDisabledImage();
        if (!path.contains(STORAGE_ENDPOINT)) {
            path = STORAGE_ENDPOINT + NAVER_BUCKET + File.separator + resultUser.getUserPrivacy().getDisabledImage();
        }
        return new UserResponse.UserDisabled(path);
    }

    @Transactional
    public void insertUserActionLog(long userId, EActionFlag action, long target){

        UserActionLog lastLog = userRepo.getLatestActionLog(userId);

        String hash = "";
        if (lastLog != null) {
            long time = lastLog.getCreateDate().toInstant().toEpochMilli() / 1000;
            hash = "hash logic";
        }
        ZonedDateTime now = ZonedDateTime.now();
        long time = now.toInstant().toEpochMilli() / 1000;
        hash = hash + "hash logic";

        ZonedDateTime longZone = ZonedDateTime.ofInstant(Instant.ofEpochSecond(time), ZoneId.of("Asia/Seoul"));

        UserActionLog actionLog = UserActionLog.builder()
                .build();
        userActionLogRepository.save(actionLog);
    }

}

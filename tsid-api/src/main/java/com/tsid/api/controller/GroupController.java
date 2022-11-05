package com.tsid.api.controller;

import com.tsid.api.exception.ErrorCode;
import com.tsid.api.exception.InvalidParameterException;
import com.tsid.api.service.GroupService;
import com.tsid.domain.enums.EErrorActionType;
import com.tsid.domain.enums.group.EGroupStatusFlag;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@ApiResponses({
        @ApiResponse(
                code = 400, message = "Bad Request", response = ErrorDto.class  , responseContainer = "Object"),
        @ApiResponse(
                code = 401, message = "UnAuthorized", response = ErrorDto.class, responseContainer = "Object"),
        @ApiResponse(
                code = 500, message = "Internal Server Error", response = ErrorDto.class  , responseContainer = "Object")
})
@RequestMapping("/api/group")
@RestController
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @ApiOperation(value = "나의 동의 그룹 리스트")
    @GetMapping("/{version}")
    public TSIDServerResponse<GroupResponse.GroupList> getGroupList(...){
        return new TSIDServerResponse(groupService.getGroupList(...));
    }

    @ApiOperation(value = "동의 그룹 생성")
    @PostMapping("/{version}")
    public TSIDServerResponse createGroup(...) {

        if (result.hasErrors()) {
            throw new InvalidParameterException(result, ErrorCode.CREATE_GROUP, EErrorActionType.NONE, ErrorMessage.INVALID_PARAMETER);
        }

        groupService.create(...);
        return new TSIDServerResponse();
    }

    @ApiOperation(value = "그룹 수정")
    @PutMapping("/{version}/{id}")
    public TSIDServerResponse updateGroup(...){
        groupService.updateGroup(...);
        return new TSIDServerResponse();
    }

    @ApiOperation(value = "그룹 구성원 권한 수정")
    @PutMapping("/{version}/{id}/role")
    public TSIDServerResponse<GroupResponse.GroupCert> updateGroupUserRole(...){

        return new TSIDServerResponse(groupService.updateGroupRole(...));
    }

    @ApiOperation(value = "그룹 상세 조회")
    @GetMapping("/{version}/{id}")
    public TSIDServerResponse<GroupResponse.GroupDetail> getGroupDetail(...){
        return new TSIDServerResponse(groupService.getGroupDetail(...));
    }

    @ApiOperation(value = "알림에서 - 그룹 상세 조회")
    @GetMapping("/{version}/{id}/alarm")
    public TSIDServerResponse<GroupResponse.GroupDetail> getGroupDetailByAlarm(...){
        return new TSIDServerResponse(groupService.getGroupDetailByAlarm(...));
    }

    @ApiOperation(value = "그룹 정보 조회(푸쉬, 알림)")
    @GetMapping("/{version}/user/{id}")
    public TSIDServerResponse<GroupResponse.GroupUserInfo> getGroupUserInfo(...){

        return new TSIDServerResponse(groupService.getGroupUserInfo(...));
    }

    @ApiOperation(value = "초대 받은 그룹 리스트 ::: 1.1.1 이후 삭제")
    @GetMapping("/{version}/invite")
    public TSIDServerResponse<GroupResponse.GroupInviteUser> inviteGroupList(
            @RequestHeader(value = "tsid-key") String encKey,
            @PathVariable("version") String version,
            Pageable pageable){

        return new TSIDServerResponse(groupService.getGroupInviteList(encKey, pageable));
    }

    @ApiOperation(value = "사용처 초대내역 active 카운트")
    @GetMapping("/{version}/invite/count")
    public TSIDServerResponse<GroupResponse.GroupAlarmCount> groupUserListCount(...){

        return new TSIDServerResponse(groupService.getGroupAlarmCount(...));
    }

    @ApiOperation(value = "초대한 사용자 리스트")
    @GetMapping("/{version}/invite/to")
    public TSIDServerResponse<GroupResponse.InviteUserGroup> inviteUserList(...){

        return new TSIDServerResponse(groupService.getInviteUserList(...));
    }

    @ApiOperation(value = "그룹에 초대 받은 리스트")
    @GetMapping("/{version}/invite/from")
    public TSIDServerResponse<GroupResponse.InviteUserGroup> invitedGroup(...){

        return new TSIDServerResponse(groupService.getInvitedGroupList(...));
    }

    @ApiOperation(value = "초대 리스트 상세값")
    @GetMapping("/{version}/invite/{type}/{targetId}")
    public TSIDServerResponse<GroupDto.InviteUserGroupTo> invitedGroup(...){

        return new TSIDServerResponse(groupService.getInviteDetail(...));
    }

    @ApiOperation(value = "그룹 초대")
    @PostMapping("/{version}/{id}/invite")
    public TSIDServerResponse inviteGroup(...) {

        if (result.hasErrors()) {
            throw new InvalidParameterException(result, ErrorCode.INVITE_USER_GROUP, EErrorActionType.NONE, ErrorMessage.INVALID_PARAMETER);
        }

        groupService.invite(...);
        return new TSIDServerResponse();
    }

    @ApiOperation(value = "그룹 삭제")
    @DeleteMapping("/{version}/{id}")
    public TSIDServerResponse<GroupResponse.GroupCert> deleteGroup(...){

        return new TSIDServerResponse(groupService.deleteGroup(...));
    }

    @ApiOperation(value = "사용자: 그룹 해제 요청")
    @PostMapping("/{version}/{id}/remove")
    public TSIDServerResponse unGroupUser(...) {

        groupService.unGroupUser(...);
        return new TSIDServerResponse();
    }

    @ApiOperation(value = "그룹 생성자: 사용자 동의 그룹 해제, 초대 취소")
    @DeleteMapping("/{version}/{id}/user/{userId}")
    public TSIDServerResponse<GroupResponse.GroupCert> deleteGroupUser(...) {

        if (result.hasErrors()) {
            throw new InvalidParameterException(result, ErrorCode.DELETE_GROUP_USER, EErrorActionType.NONE, ErrorMessage.INVALID_PARAMETER);
        }

        return new TSIDServerResponse(groupService.deleteGroupUser(...));
    }

    @ApiOperation(value = "사용자: 그룹 초대 승인/거절")
    @PutMapping("/{version}/{id}/invite")
    public TSIDServerResponse updateInviteGroup(...) {

        if (result.hasErrors()) {
            throw new InvalidParameterException(result, ErrorCode.UPDATE_INVITE_GROUP, EErrorActionType.NONE, ErrorMessage.INVALID_PARAMETER);
        }

        groupService.updateInviteGroup(...);
        return new TSIDServerResponse();
    }

    @ApiOperation(value = "그룹 생성자: 그룹 해제 승인/거절")
    @PutMapping("/{version}/{id}/remove/{userId}")
    public TSIDServerResponse updateGroupRemove(...) {

        if (result.hasErrors()) {
            throw new InvalidParameterException(result, ErrorCode.UPDATE_REMOVE_GROUP, EErrorActionType.NONE, ErrorMessage.INVALID_PARAMETER);
        }

        groupService.updateRemoveGroup(...);
        return new TSIDServerResponse();
    }

}

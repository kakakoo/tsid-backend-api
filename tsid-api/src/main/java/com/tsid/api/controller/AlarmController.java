package com.tsid.api.controller;

import com.tsid.api.service.AlarmService;
import com.tsid.domain.enums.notification.EAlarmFlag;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;


@ApiResponses({
        @ApiResponse(
                code = 400, message = "Bad Request", response = ErrorDto.class  , responseContainer = "Object"),
        @ApiResponse(
                code = 401, message = "UnAuthorized", response = ErrorDto.class, responseContainer = "Object"),
        @ApiResponse(
                code = 500, message = "Internal Server Error", response = ErrorDto.class  , responseContainer = "Object")
})
@RequestMapping("/api/alarm")
@RestController
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alarmService;

    @ApiOperation(value = "알람 리스트")
    @GetMapping("/{version}")
    public TSIDServerResponse<AlarmResponse.AlarmList> getAlarmList(...) {

        return new TSIDServerResponse(alarmService.getAlarmList(...));
    }

    @ApiOperation(value = "알람 갯수")
    @GetMapping("/{version}/count")
    public TSIDServerResponse<AlarmResponse.AlarmCount> getAlarmCount(...){
        return new TSIDServerResponse(alarmService.getAlarmCount(...));
    }

    @ApiOperation(value = "비회원일때 초대받은 그룹 있는지 확인")
    @GetMapping("/{version}/exist")
    public TSIDServerResponse<AlarmResponse.AlarmExist> getAlarmExist(...){
        return new TSIDServerResponse(alarmService.getAlarmExist(...));
    }


}

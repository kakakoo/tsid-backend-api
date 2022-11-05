package com.tsid.api.util;

import com.google.gson.Gson;
import com.tsid.api.exception.ErrorCode;
import com.tsid.api.exception.TSIDServerException;
import com.tsid.domain.enums.EErrorActionType;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Date;
import java.util.TimeZone;

public class ConvertUtil {


    public static UserDto.ClientDetail base64EncodingStrToDecodeClientDetail(String encodingStr){

        try {
            byte[] decodedBytes = Base64.getDecoder().decode(encodingStr);
            String decodedString = new String(decodedBytes);

            return new Gson().fromJson(decodedString, UserDto.ClientDetail.class);
        } catch (Exception e){
            throw new TSIDServerException(ErrorCode.PARSE_JSON, EErrorActionType.NONE, "secret 오류");
        }


    }

    public static ZonedDateTime convertLongToZonedDateTime(Long time){
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(time), ZoneId.systemDefault());

    }

    public static LocalDateTime convertLocalDateTimeNowTrans(){
        Timestamp timestamp = Timestamp.valueOf(LocalDateTime.now());
        long time = timestamp.getTime();
        time = (time / 1000) * 1000;

        return LocalDateTime.ofInstant(Instant.ofEpochMilli(time), TimeZone.getDefault().toZoneId());
    }

    public static String convertTimeStampToString(Timestamp timestamp){
        String timestampString = String.valueOf(timestamp.getTime());

        if(timestampString.length()!=16){
            for(int i=timestampString.length(); i<16; i++){
                timestampString += 0;
            }
        }

        return timestampString;
    }

    public static String localDateTimeToString(LocalDateTime localTime, String format) {
        return localTime.format(DateTimeFormatter.ofPattern(format));
    }

    public static ZonedDateTime convertDateToZonedDateTime(Date date){
        ZoneId defaultZoneId = ZoneId.systemDefault();
        // Convert Date to Instant.
        Instant instant = date.toInstant();
        // Instant + default time zone.
        return instant.atZone(defaultZoneId);
    }

}

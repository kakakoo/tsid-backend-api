package com.tsid.api.service;


import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tsid.api.exception.ErrorCode;
import com.tsid.api.exception.TSIDServerException;
import com.tsid.domain.enums.EErrorActionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.tsid.domain.entity.code.QCode.code1;

@Slf4j
@Service
@RequiredArgsConstructor
public class CodeService {

    private final JPAQueryFactory jpaQueryFactory;

    public CodeResponse.CodeList getCodeList(Long groupId){
        List<CodeDto.Code> result = jpaQueryFactory
                .select(Projections.bean(
                        CodeDto.Code.class,
                        code1.id,
                        code1.code,
                        code1.image))
                .from(code1)
                .where(code1.groupId.eq(groupId).and(code1.deleteDate.isNull()))
                .fetch();

        if(result.isEmpty()) throw new TSIDServerException(ErrorCode.GET_CODE_LIST, EErrorActionType.NONE, "존재하는 코드 그룹리스트가 없습니다.");

        return new CodeResponse.CodeList(result);
    }


}

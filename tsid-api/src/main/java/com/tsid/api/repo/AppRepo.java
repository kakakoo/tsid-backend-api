package com.tsid.api.repo;

import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tsid.domain.entity.serverStatus.ServerStatus;
import com.tsid.domain.enums.EClientPlatformType;
import com.tsid.domain.enums.EStatusFlag;
import com.tsid.domain.enums.banner.EBannerTypeFlag;
import com.tsid.domain.enums.term.ETermGroupFlag;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

import static com.tsid.domain.entity.banner.QBanner.banner;
import static com.tsid.domain.entity.serverInfo.QServerInfo.serverInfo;
import static com.tsid.domain.entity.serverStatus.QServerStatus.serverStatus;
import static com.tsid.domain.entity.term.QTerm.term;
import static com.tsid.domain.entity.termGroup.QTermGroup.termGroup;
import static com.tsid.domain.entity.version.QVersion.version;

@Component
@RequiredArgsConstructor
public class AppRepo {

    private final JPAQueryFactory jpaQueryFactory;

    public List<TermDto.Term> getTermList(ETermGroupFlag type) {
        return jpaQueryFactory
                .select(Projections.bean(
                        TermDto.Term.class,
                        term.id,
                        term.termUrl.as("url"),
                        termGroup.termDate,
                        termGroup.type,
                        term.isEssential))
                .from(term)
                .innerJoin(termGroup)
                .on(term.groupId.eq(termGroup.id))
                .where(eqType(type))
                .fetch();
    }

    private BooleanExpression eqType(ETermGroupFlag type) {
        if (type == null) {
            return null;
        }
        return termGroup.type.eq(type);
    }

    public BannerResponse.AlarmBanner getAlarmBanner() {
        ZonedDateTime nowTime = ZonedDateTime.now();
        return jpaQueryFactory
                .select(Projections.bean(
                        BannerResponse.AlarmBanner.class,
                        banner.id,
                        banner.title,
                        banner.description,
                        banner.linkUrl))
                .from(banner)
                .where(banner.status.eq(EStatusFlag.ACTIVE),
                        banner.bannerType.eq(EBannerTypeFlag.ALARM),
                        banner.startDate.before(nowTime),
                        banner.endDate.after(nowTime))
                .limit(1)
                .orderBy(banner.endDate.asc())
                .fetchOne();
    }
}

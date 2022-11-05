package com.tsid.api.service;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tsid.domain.enums.EStatusFlag;
import com.tsid.domain.enums.banner.EBannerTypeFlag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

import static com.tsid.domain.entity.banner.QBanner.banner;

@Service
@Slf4j
@RequiredArgsConstructor
public class BannerService {

    private final JPAQueryFactory jpaQueryFactory;

    @Transactional(readOnly = true)
    public BannerResponse.BannerBody getBannerList() {

        List<BannerResponse.BannerDetail> bannerDetailList = jpaQueryFactory
                .select(Projections.bean(
                        BannerResponse.BannerDetail.class,
                        banner.id,
                        banner.title,
                        banner.bannerImage,
                        banner.linkType,
                        banner.linkUrl))
                .from(banner)
                .where(banner.bannerType.eq(EBannerTypeFlag.MAIN),
                        banner.status.eq(EStatusFlag.ACTIVE),
                        banner.startDate.before(ZonedDateTime.now()),
                        banner.endDate.after(ZonedDateTime.now()))
                .orderBy(banner.order.asc())
                .fetch();

        return new BannerResponse.BannerBody(bannerDetailList);
    }
}

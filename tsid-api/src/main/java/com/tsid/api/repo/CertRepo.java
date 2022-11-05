package com.tsid.api.repo;

import com.querydsl.core.types.ExpressionUtils;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.CaseBuilder;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tsid.domain.entity.certRole.CertRole;
import com.tsid.domain.entity.geoIpBlock.GeoIpBlock;
import com.tsid.domain.entity.groupCert.GroupCert;
import com.tsid.domain.entity.groupUpdateHistory.GroupUpdateHistory;
import com.tsid.domain.entity.permission.Permission;
import com.tsid.domain.entity.user.QUser;
import com.tsid.domain.entity.userHasGroup.QUserHasGroup;
import com.tsid.domain.enums.ECertListType;
import com.tsid.domain.enums.cert.ECertFlag;
import com.tsid.domain.enums.cert.ECertHistoryFlag;
import com.tsid.domain.enums.group.EGroupPositionFlag;
import com.tsid.domain.enums.group.EGroupStatusFlag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.querydsl.core.types.ExpressionUtils.count;
import static com.tsid.domain.entity.certRole.QCertRole.certRole;
import static com.tsid.domain.entity.company.QCompany.company;
import static com.tsid.domain.entity.geoCity.QGeoCity.geoCity;
import static com.tsid.domain.entity.geoIpBlock.QGeoIpBlock.geoIpBlock;
import static com.tsid.domain.entity.group.QGroup.group;
import static com.tsid.domain.entity.groupCert.QGroupCert.groupCert;
import static com.tsid.domain.entity.groupCertHistory.QGroupCertHistory.groupCertHistory;
import static com.tsid.domain.entity.groupHasCompany.QGroupHasCompany.groupHasCompany;
import static com.tsid.domain.entity.groupUpdateHistory.QGroupUpdateHistory.groupUpdateHistory;
import static com.tsid.domain.entity.permission.QPermission.permission;
import static com.tsid.domain.entity.user.QUser.user;
import static com.tsid.domain.entity.userHasGroup.QUserHasGroup.userHasGroup;

@Component
@RequiredArgsConstructor
public class CertRepo {

    private final JPAQueryFactory jpaQueryFactory;

    public List<CertDto.Cert> getCertList(String uuid, ECertListType type, Pageable pageable){

        QUser maker = new QUser("maker");
        QUserHasGroup makerHasGroup = new QUserHasGroup("makerHasGroup");

        return jpaQueryFactory
                .select(Projections.fields(
                        CertDto.Cert.class,
                        groupCert.id.as("id"),
                        groupCert.status.as("status"),
                        new CaseBuilder().when(groupCert.status.eq(ECertFlag.SUCCESS)).then(true).otherwise(false).as("isCert"),
                        new CaseBuilder().when(groupCert.status.eq(ECertFlag.EXPIRED)).then(true).otherwise(false).as("isExpired"),
                        groupCert.certValue.as("consenter"),
                        groupCert.referrerValue.as("referrer"),
                        groupCert.certCount.as("certCount"),
                        groupCert.isGroup,
                        ExpressionUtils.as(JPAExpressions
                                .select(count(groupCertHistory))
                                .from(groupCertHistory)
                                .where(groupCert.id.eq(groupCertHistory.groupCert.id),
                                        groupCertHistory.status.eq(ECertHistoryFlag.SUCCESS)), "certValue"),
                        group.name.as("name"),
                        company.image.as("image"),
                        certRole.name.as("roleName"),
                        userHasGroup.position,
                        new CaseBuilder().when(userHasGroup.position.eq(EGroupPositionFlag.MAKER)).then(true).otherwise(false).as("isMaker"),
                        maker.name.as("makerName"),
                        groupCert.createDate.as("createDate")))
                .from(userHasGroup)
                .join(userHasGroup.group, group)
                .join(groupCert).on(group.id.eq(groupCert.group.id))
                .join(groupCert.company, company)
                .join(groupCert.certRole, certRole)
                .join(makerHasGroup).on(group.id.eq(makerHasGroup.group.id),
                        makerHasGroup.position.eq(EGroupPositionFlag.MAKER))
                .join(maker).on(makerHasGroup.user.id.eq(maker.id))
                .where(userHasGroup.user.uuid.eq(uuid),
                        userHasGroup.status.in(EGroupStatusFlag.RELEASE, EGroupStatusFlag.ACTIVE),
                        userHasGroup.joinDate.loe(groupCert.createDate),
                        isCertTypeEq(type))
                .orderBy(new CaseBuilder().when(groupCert.status.eq(ECertFlag.PROGRESS)).then(0).otherwise(1).asc(),
                        groupCert.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }

    private BooleanExpression isCertTypeEq(ECertListType type) {
        if (type == null || type.equals(ECertListType.ALL)) {
            return null;
        } else if (type.equals(ECertListType.MAKER)) {
            return userHasGroup.position.eq(EGroupPositionFlag.MAKER);
        } else {
            return userHasGroup.position.ne(EGroupPositionFlag.MAKER);
        }
    }

    public String getCertRoleCodeByCertId(long certId){
        return jpaQueryFactory
                .select(certRole.code)
                .from(groupCert)
                .join(groupCert.certRole, certRole)
                .where(groupCert.id.eq(certId))
                .fetchOne();
    }

    public List<Long> getMakersCompanyIds(long userId){
        return jpaQueryFactory
                .select(groupHasCompany.company.id)
                .from(userHasGroup)
                .join(userHasGroup.group, group)
                .join(groupHasCompany).on(group.id.eq(groupHasCompany.group.id))
                .where(userHasGroup.user.id.eq(userId),
                        userHasGroup.position.eq(EGroupPositionFlag.MAKER),
                        userHasGroup.status.eq(EGroupStatusFlag.ACTIVE))
                .fetch();
    }

    public List<CertDto.Cert> getCertGroupListByGroupId(String uuid, long groupId, Pageable pageable){
        return jpaQueryFactory
                .select(Projections.fields(
                    CertDto.Cert.class,
                    groupCert.id.as("id"),
                    groupCert.status.as("status"),
                    new CaseBuilder().when(groupCert.status.eq(ECertFlag.SUCCESS)).then(true).otherwise(false).as("isCert"),
                    new CaseBuilder().when(groupCert.status.eq(ECertFlag.EXPIRED)).then(true).otherwise(false).as("isExpired"),
                    groupCert.certValue.as("consenter"),
                    groupCert.referrerValue.as("referrer"),
                    groupCert.certCount.as("certCount"),
                    groupCert.isGroup,
                    ExpressionUtils.as(JPAExpressions
                            .select(count(groupCertHistory))
                            .from(groupCertHistory)
                            .where(groupCert.id.eq(groupCertHistory.groupCert.id),
                                    groupCertHistory.status.eq(ECertHistoryFlag.SUCCESS)), "certValue"),
                    group.name.as("name"),
                    company.image.as("image"),
                    certRole.name.as("roleName"),
                    groupCert.createDate.as("createDate")))
                .from(userHasGroup)
                .join(group).on(userHasGroup.group.id.eq(group.id),
                        group.id.eq(groupId))
                .join(groupCert).on(group.id.eq(groupCert.group.id))
                .join(groupCert.company, company)
                .join(groupCert.certRole, certRole)
                .where(userHasGroup.user.uuid.eq(uuid),
                        userHasGroup.joinDate.loe(groupCert.createDate),
                        userHasGroup.status.in(EGroupStatusFlag.ACTIVE, EGroupStatusFlag.RELEASE))
                .orderBy(new CaseBuilder().when(groupCert.status.eq(ECertFlag.PROGRESS)).then(0).otherwise(1).asc(),
                        groupCert.id.desc())
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .fetch();
    }

    public List<GroupCert> getCertListByGroupId(Long groupId) {
        return jpaQueryFactory
                .selectFrom(groupCert)
                .where(groupCert.group.id.eq(groupId))
                .fetch();
    }

    public Long getProgressGroupCert(Long userId, Long groupId) {
        return jpaQueryFactory
                .select(count(groupCert))
                .from(userHasGroup)
                .join(group).on(userHasGroup.group.id.eq(group.id),
                        group.id.eq(groupId))
                .join(groupCert).on(group.id.eq(groupCert.group.id),
                        groupCert.status.eq(ECertFlag.PROGRESS))
                .where(userHasGroup.user.id.eq(userId),
                        userHasGroup.status.in(EGroupStatusFlag.ACTIVE, EGroupStatusFlag.RELEASE))
                .fetchOne();
    }

}

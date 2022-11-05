package com.tsid.api.repo;

import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.tsid.domain.entity.company.Company;
import com.tsid.domain.enums.EStatusFlag;
import com.tsid.domain.enums.group.EGroupPositionFlag;
import com.tsid.domain.enums.group.EGroupStatusFlag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.querydsl.core.types.ExpressionUtils.count;
import static com.tsid.domain.entity.certRole.QCertRole.certRole;
import static com.tsid.domain.entity.company.QCompany.company;
import static com.tsid.domain.entity.companyCallback.QCompanyCallback.companyCallback;
import static com.tsid.domain.entity.companyDetail.QCompanyDetail.companyDetail;
import static com.tsid.domain.entity.companyHasCertRole.QCompanyHasCertRole.companyHasCertRole;
import static com.tsid.domain.entity.group.QGroup.group;
import static com.tsid.domain.entity.groupHasCompany.QGroupHasCompany.groupHasCompany;
import static com.tsid.domain.entity.userHasCompany.QUserHasCompany.userHasCompany;
import static com.tsid.domain.entity.userHasGroup.QUserHasGroup.userHasGroup;

@Component
@RequiredArgsConstructor
public class CompanyRepo {

    private final JPAQueryFactory jpaQueryFactory;

    public List<Long> getCompanyIdsByGroupIds(Long userId){
        return jpaQueryFactory
                .select(groupHasCompany.company.id)
                .from(userHasGroup)
                .join(group).on(userHasGroup.group.id.eq(group.id),
                        group.isActive.isTrue())
                .join(groupHasCompany).on(group.id.eq(groupHasCompany.group.id))
                .where(userHasGroup.user.id.eq(userId),
                        userHasGroup.status.eq(EGroupStatusFlag.ACTIVE),
                        userHasGroup.position.eq(EGroupPositionFlag.MAKER))
                .fetch();
    }

    public Company getCompanyByGroupId(Long groupId) {
        return jpaQueryFactory
                .select(groupHasCompany.company)
                .from(groupHasCompany)
                .where(groupHasCompany.group.id.eq(groupId))
                .fetchOne();
    }

    public void deleteCompanyGroup(Long companyId, Long groupId) {
        jpaQueryFactory
                .delete(groupHasCompany)
                .where(groupHasCompany.group.id.eq(groupId),
                        groupHasCompany.company.id.eq(companyId))
                .execute();
    }


    public CompanyDto.CompanyInfo getCustomCompanyByGroupId(Long groupId) {
        return jpaQueryFactory
                .select(Projections.bean(
                        CompanyDto.CompanyInfo.class,
                        company.id,
                        company.name,
                        company.image))
                .from(groupHasCompany)
                .innerJoin(company)
                .on(groupHasCompany.company.id.eq(company.id))
                .where(groupHasCompany.group.id.eq(groupId))
                .fetchOne();
    }


    public Company getCompanyByClientIdAndCertRole(String clientId, String certCode) {
        return jpaQueryFactory
                .select(company)
                .from(companyDetail)
                .join(companyDetail.company, company)
                .join(company.companyHasCertRoles, companyHasCertRole)
                .join(companyHasCertRole.certRole, certRole)
                .where(companyDetail.clientId.eq(clientId),
                        certRole.code.eq(certCode))
                .fetchOne();
    }

    public Long getCheckCompanyUser(Long userId) {
        return jpaQueryFactory
                .select(count(userHasCompany))
                .from(userHasCompany)
                .where(userHasCompany.user.id.eq(userId),
                        userHasCompany.status.eq(EGroupStatusFlag.ACTIVE))
                .fetchOne();
    }

}

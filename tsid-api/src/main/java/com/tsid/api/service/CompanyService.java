package com.tsid.api.service;

import com.tsid.api.repo.CompanyRepo;
import com.tsid.api.repo.GroupRepo;
import com.tsid.api.util.SecurityUtil;
import com.tsid.domain.entity.user.User;
import com.tsid.domain.entity.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final GroupRepo groupRepo;
    private final CompanyRepo companyRepo;
    private final UserRepository userRepository;

    public CompanyResponse.CompanyList getCompanyList(...){
        /**
         * 사용처 리스트 호출
         * 사용자가 MAKER 인 그룹 사용처랑 비교해서 반환
         */
        User userInfo = userRepository.getUserByUuid(SecurityUtil.getCurrentUserUuid());

        /**
         * 사용자가 maker인 그룹 사용처랑, 운영가능한 사용처 비교
         */
        List<Long> resultGroupHasCompany = companyRepo.getCompanyIdsByGroupIds(userInfo.getId());
        List<CompanyDto.Company> resultMapper = companyRepo.getActiveCompanyListByPaging(pageable);

        if(resultGroupHasCompany.isEmpty()){

            if(resultMapper.isEmpty()){
                return new CompanyResponse.CompanyList(new ArrayList<>());
            }

            List<CompanyDto.Company> resultList = resultMapper
                    .stream()
                    .map(p ->
                            new CompanyDto.Company(p.getId(), p.getName(), p.getImage(), false))
                    .collect(Collectors.toList());

            return new CompanyResponse.CompanyList(resultList);
        }


        List<CompanyDto.Company> resultList = new ArrayList<>();
        for(CompanyDto.Company company: resultMapper){
            Boolean isMaid = false;

            if (resultGroupHasCompany.contains(company.getId())) {
                isMaid = true;
            }

            CompanyDto.Company newCompany = CompanyDto.Company
                    .builder()
                    .id(company.getId())
                    .name(company.getName())
                    .image(company.getImage())
                    .isMaid(isMaid)
                    .build();

            resultList.add(newCompany);
        }

        return new CompanyResponse.CompanyList(resultList);
    }
}

package com.devsss.onlyone.server.repository;

import com.devsss.onlyone.server.entity.CertInfoEntity;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;

@Component
public interface CertInfoRepository extends CrudRepository<CertInfoEntity, String> {

    CertInfoEntity findFirstByDomainNameOrderByInsertTimeDesc(String domainName);

    CertInfoEntity findFirstByCertIdOrderByInsertTimeDesc(String certId);
}

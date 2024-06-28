package com.devsss.onlyone.server.repository;

import com.devsss.onlyone.server.entity.ClientEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public interface ClientRepository extends CrudRepository<ClientEntity,String> {

    Page<ClientEntity> findAll(Pageable pageable);

    Page<ClientEntity> findAllByIdContainsAndBzContainsOrderByLrrqDesc(String id, String bz,Pageable pageable);

    @Transactional
    @Query("update ClientEntity n set n.yxbz = :yxbz where n.id = :id")
    @Modifying
    int updateYxbzById(String id,boolean yxbz);
}

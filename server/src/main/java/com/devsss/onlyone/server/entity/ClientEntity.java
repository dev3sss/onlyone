package com.devsss.onlyone.server.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.sql.Timestamp;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(name = "client")
@EntityListeners(AuditingEntityListener.class)
public class ClientEntity {

    @Id
    private String id;

    @Column
    private String license;

    @Column
    private String msgKey;

    @Column
    private String bz;

    @Column(updatable = false)
    private boolean yxbz;

    @Column(updatable = false)
    @CreatedDate
    private Timestamp lrrq;

    @Column
    @LastModifiedDate
    private Timestamp xgrq;
}

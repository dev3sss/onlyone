package com.devsss.onlyone.server.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.sql.Timestamp;

@Data
@Entity
@EntityListeners(AuditingEntityListener.class)
@Table(name = "cert_info")
public class CertInfoEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    String id;

    String certId;

    String domainName;

    Timestamp certBeginTime;

    Timestamp certEndTime;

    @Column(updatable = false)
    @CreatedDate
    Timestamp insertTime;

    // 下载标志
    boolean xzbz;

    // 本地保存路径
    String path;

    // 证书状态：0 = 审核中，1 = 已通过，2 = 审核失败，3 = 已过期，4 = 已添加DNS记录，5 = 企业证书，待提交，
    // 6 = 订单取消中，7 = 已取消，8 = 已提交资料， 待上传确认函，9 = 证书吊销中，10 = 已吊销，
    // 11 = 重颁发中，12 = 待上传吊销确认函。
    Integer status;
}

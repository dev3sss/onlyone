package com.devsss.onlyone.server.service;

import com.devsss.onlyone.server.config.ServerConfig;
import com.devsss.onlyone.server.entity.CertInfoEntity;
import com.devsss.onlyone.server.repository.CertInfoRepository;
import com.devsss.onlyone.server.util.GyUtils;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.provider.ProfileCredentialsProvider;
import com.tencentcloudapi.ssl.v20191205.SslClient;
import com.tencentcloudapi.ssl.v20191205.models.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class TencentCloudService {

    Credential cred;
    SslClient sslClient;

    CertInfoRepository certInfoRepository;

    ServerConfig serverConfig;

    KeyStoreService keyStoreService;

    String rootDir;

    public TencentCloudService(CertInfoRepository certInfoRepository, ServerConfig serverConfig,
                               KeyStoreService keyStoreService) throws TencentCloudSDKException {
        this.certInfoRepository = certInfoRepository;
        this.serverConfig = serverConfig;
        this.keyStoreService = keyStoreService;
        this.rootDir = serverConfig.getFilepath() + "/cert/";
        File file = new File(rootDir);
        if (!file.exists()) {
            file.mkdirs();
        }
        cred = new ProfileCredentialsProvider().getCredentials();
        sslClient = new SslClient(cred, "ap-shanghai");
    }

    /**
     * 申请证书
     *
     * @param domainName 域名
     * @return 证书信息
     */
    public CertInfoEntity applyCertificate(String domainName) throws TencentCloudSDKException, URISyntaxException, IOException {
        // 先判断已有证书的有效期是否过期
        List<CertInfoEntity> certInfoEntities = describeCertificates(domainName);
        if (!certInfoEntities.isEmpty()) {
            // 取申请日期最大的记录
            CertInfoEntity lastEntity = certInfoEntities.getFirst();
            for (CertInfoEntity certInfoEntity : certInfoEntities) {
                if (certInfoEntity.getInsertTime().after(lastEntity.getInsertTime())) {
                    lastEntity = certInfoEntity;
                }
            }
            // 判断是否审核通过
            if (lastEntity.getStatus() != 1) {
                log.info("域名 {} 的证书最近一次申请未审核通过，需要前往控制台查看原因", domainName);
                certInfoRepository.save(lastEntity);
                return lastEntity;
            }
            if (lastEntity.getCertEndTime().after(new Timestamp(System.currentTimeMillis() - 1000 * 60 * 60 * 24 * 3))) {
                log.info("域名 {} 的证书有效期剩余时间超过3天，无需申请", domainName);
                return lastEntity;
            }
        }
        ApplyCertificateRequest req = new ApplyCertificateRequest();
        req.setDomainName(domainName);
        // DNS_AUTO = 自动DNS验证，DNS = 手动DNS验证，FILE = 文件验证
        req.setDvAuthMethod("DNS_AUTO");
        ApplyCertificateResponse response = sslClient.ApplyCertificate(req);
        String certificateId = response.getCertificateId();
        // 需要等待验证域名，先记录下证书ID
        CertInfoEntity certInfo = new CertInfoEntity();
        certInfo.setCertId(certificateId);
        certInfo.setDomainName(domainName);
        return certInfoRepository.save(certInfo);
    }

    /**
     * 下载证书
     *
     * @param certInfo 证书信息
     * @return 证书保存路径
     * @throws TencentCloudSDKException 异常
     * @throws URISyntaxException       异常
     * @throws IOException              异常
     */
    public String downloadCertificate(CertInfoEntity certInfo) throws TencentCloudSDKException, URISyntaxException, IOException {
        // 获取证书下载地址
        DescribeDownloadCertificateUrlRequest downReq = new DescribeDownloadCertificateUrlRequest();
        downReq.setCertificateId(certInfo.getCertId());
        downReq.setServiceType("nginx");
        DescribeDownloadCertificateUrlResponse downResp = sslClient.DescribeDownloadCertificateUrl(downReq);
        String certUrl = downResp.getDownloadCertificateUrl();
        log.info("域名 {}，证书ID: {}, 证书下载地址: {}", certInfo.getDomainName(), certInfo.getCertId(), certUrl);
        // 文件名
        String reqUrl = certUrl.split("\\?")[0];
        String suffix = reqUrl.substring(reqUrl.lastIndexOf("."));
        // 证书保存路径
        String certPath = rootDir + certInfo.getCertId() + suffix;
        // 下载证书
        String zipFile = GyUtils.downloadFile(certPath, certUrl);
        // 解压
        String unZipPath = rootDir + certInfo.getCertId();
        GyUtils.unZip(zipFile, unZipPath);
        // 删除压缩包
        GyUtils.deleteFile(zipFile);
        certInfo.setPath(unZipPath);
        certInfo.setXzbz(true);
        certInfoRepository.save(certInfo);
        return unZipPath;
    }

    public List<CertInfoEntity> describeCertificates(String domainName) throws TencentCloudSDKException {
        List<CertInfoEntity> certInfoEntities = new ArrayList<>();
        DescribeCertificatesRequest req = new DescribeCertificatesRequest();
        req.setOffset(0L);
        // 腾讯云免费证书只能有50个
        req.setLimit(50L);
        DescribeCertificatesResponse resp = sslClient.DescribeCertificates(req);
        for (Certificates certificate : resp.getCertificates()) {
            if (certificate.getDomain().contains(domainName) || GyUtils.isNull(domainName)) {
                CertInfoEntity entity = new CertInfoEntity();
                entity.setCertId(certificate.getCertificateId());
                entity.setDomainName(certificate.getDomain());
                entity.setCertBeginTime(Timestamp.valueOf(certificate.getCertBeginTime()));
                entity.setCertEndTime(Timestamp.valueOf(certificate.getCertEndTime()));
                entity.setInsertTime(Timestamp.valueOf(certificate.getInsertTime()));
                entity.setStatus(Math.toIntExact(certificate.getStatus()));
                certInfoEntities.add(entity);
            }
        }
        return certInfoEntities;
    }

    private void addCertToKeyStore(String certDir) throws Exception {
        String keyPath = GyUtils.findFilePathBySuffix(certDir, ".key");
        String crtPath = GyUtils.findFilePathBySuffix(certDir, ".pem");
        if (GyUtils.isNull(keyPath) || GyUtils.isNull(crtPath)) {
            throw new RuntimeException("在[" + certDir + "]没有找到.key 或 .pem 文件");
        }
        keyStoreService.loadCertFileToJks(keyPath, crtPath);
    }

    public CertInfoEntity addToJks(CertInfoEntity certInfo) throws Exception {
        if (certInfo.getStatus() != 1) {
            log.info("域名 {} 的证书申请未通过，无需下载", certInfo.getDomainName());
            throw new RuntimeException("域名 " + certInfo.getDomainName() + " 的证书还未通过");
        }
        String certDir = rootDir + certInfo.getCertId();
        // 查看是否有此文件夹
        File file = new File(certDir);
        if (!file.exists()) {
            certDir = downloadCertificate(certInfo);
        }
        addCertToKeyStore(certDir);
        certInfo.setXzbz(true);
        return certInfoRepository.save(certInfo);
    }

}

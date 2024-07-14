package com.devsss.onlyone.server.service;

import com.devsss.onlyone.core.protocol.ssl.SslServer;
import com.devsss.onlyone.core.util.KeyStoreUtils;
import com.devsss.onlyone.server.config.ServerConfig;
import com.devsss.onlyone.server.vo.PemFileInfo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.bouncycastle.asn1.x500.RDN;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Enumeration;

@AllArgsConstructor
@Slf4j
@Service
public class KeyStoreService {

    ServerConfig config;

    public ArrayList<PemFileInfo> getAllKeyInfo() throws Exception {
        final ArrayList<PemFileInfo> pemInfo = new ArrayList<>();
        KeyStore keyStore = SslServer.keyStore;
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            PemFileInfo fileInfo = new PemFileInfo();
            String alias = aliases.nextElement();
            getKeyInfo(alias, keyStore, fileInfo);
            pemInfo.add(fileInfo);
        }
        return pemInfo;
    }

    public PemFileInfo getKeyInfo(String alias) throws Exception {
        KeyStore keyStore = SslServer.keyStore;
        PemFileInfo fileInfo = new PemFileInfo();
        if (keyStore.containsAlias(alias)) {
            getKeyInfo(alias, keyStore, fileInfo);
        }
        return fileInfo;
    }

    private void getKeyInfo(String alias, KeyStore keyStore, PemFileInfo info) throws KeyStoreException, IOException, CertificateEncodingException, NoSuchAlgorithmException, UnrecoverableKeyException {
        Certificate[] certificates = keyStore.getCertificateChain(alias);
        if (certificates.length > 0) {
            X509CertificateHolder certificateHolder = new X509CertificateHolder(certificates[0].getEncoded());
            info.setNotBefore(certificateHolder.getNotBefore());
            info.setNotAfter(certificateHolder.getNotAfter());
            info.setIssuer(certificateHolder.getIssuer().toString());
            info.setSubject(certificateHolder.getSubject().toString());
        }
        Key key = keyStore.getKey(alias, SslServer.keyPassword.toCharArray());
        info.setAlgorithm(key.getAlgorithm());
        info.setAlias(alias);
    }

    /**
     * 保存将证书相关文件解析并临时保存
     *
     * @param file 文件
     * @return 解析结果
     * @throws IOException 异常
     */
    public PemFileInfo savePem(MultipartFile file) throws IOException {
        PemFileInfo fileInfo = new PemFileInfo();
        fileInfo.setOriginalFilename(file.getOriginalFilename());
        fileInfo.setSize(file.getSize());
        try (PEMParser pemParser = new PEMParser(new InputStreamReader(file.getInputStream()))) {
            Object obj = pemParser.readObject();
            String path = config.getFilepath() + "/temp/";
            File _f = new File(path);
            if (!_f.exists()) {
                _f.mkdirs();
            }
            String name = file.getOriginalFilename() + "_" + RandomStringUtils.randomAlphabetic(6);
            if (obj instanceof PEMKeyPair pemKeyPair) {
                try (FileOutputStream out = new FileOutputStream(path + name + ".key")) {
                    out.write(file.getBytes());
                }
                fileInfo.setSaveFilename(name + ".key");
                fileInfo.setFileType(".key");
                KeyPair keyPair = KeyStoreUtils.pemKeyPairToKeyPair(pemKeyPair);
                fileInfo.setAlgorithm(keyPair.getPrivate().getAlgorithm());
            } else if (obj instanceof X509CertificateHolder certificateHolder) {
                try (FileOutputStream out = new FileOutputStream(path + name + ".pem")) {
                    out.write(file.getBytes());
                }
                fileInfo.setSaveFilename(name + ".pem");
                fileInfo.setFileType(".pem");
                fileInfo.setNotBefore(certificateHolder.getNotBefore());
                fileInfo.setNotAfter(certificateHolder.getNotAfter());
                fileInfo.setIssuer(certificateHolder.getIssuer().toString());
                fileInfo.setSubject(certificateHolder.getSubject().toString());
            } else {
                throw new IOException("不支持的格式");
            }
        }
        return fileInfo;
    }

    /**
     * 将证书内容写入keystore文件
     *
     * @param key  key
     * @param cert cert
     * @throws Exception 异常
     */
    public void saveZs(PemFileInfo key, PemFileInfo cert) throws Exception {
        final String path = config.getFilepath() + "/temp/";
        loadCertFileToJks(path + key.getSaveFilename(), path + cert.getSaveFilename());
    }

    public void loadCertFileToJks(String keyPath, String certPath) throws Exception {
        KeyPair keyPair = KeyStoreUtils.loadPEMKey(keyPath);
        Object[] certObj = KeyStoreUtils.loadPEMCert(certPath);
        X509CertificateHolder o = (X509CertificateHolder) certObj[0];
        Certificate certificate = (Certificate) certObj[1];
        String alias = "";
        RDN[] rdNs = o.getSubject().getRDNs(BCStyle.CN);
        if (rdNs != null && rdNs.length > 0) {
            alias = rdNs[0].getFirst().getValue().toString();
        } else {
            alias = o.getSubject().toString();
        }
        SslServer.keyStore.setKeyEntry(alias, keyPair.getPrivate(), SslServer.keyPassword.toCharArray(),
                new Certificate[]{certificate});
        SslServer.keyStore.store(new FileOutputStream(SslServer.kmsFile), SslServer.keyStorePassword.toCharArray());
    }
}

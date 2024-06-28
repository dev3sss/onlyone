package com.devsss.onlyone.core.util;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMException;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigInteger;
import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Date;
import java.util.Random;

@Slf4j
public class KeyStoreUtils {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * 生成Subject信息
     *
     * @param C  Country Name (国家代号),eg: CN
     * @param ST State or Province Name (洲或者省份),eg: Beijing
     * @param L  Locality Name (城市名),eg: Beijing
     * @param O  Organization Name (可以是公司名称),eg: 北京创新乐知网络技术有限公司
     * @param OU Organizational Unit Name (可以是单位部门名称)
     * @param CN Common Name (服务器ip或者域名),eg: 192.168.30.71 or www.baidu.com
     * @return X500Name Subject
     */
    public static X500Name generateSubject(String C, String ST, String L,
                                           String O, String OU, String CN) {
        X500NameBuilder x500NameBuilder = new X500NameBuilder();
        x500NameBuilder.addRDN(BCStyle.C, C);
        x500NameBuilder.addRDN(BCStyle.ST, ST);
        x500NameBuilder.addRDN(BCStyle.L, L);
        x500NameBuilder.addRDN(BCStyle.O, O);
        x500NameBuilder.addRDN(BCStyle.OU, OU);
        x500NameBuilder.addRDN(BCStyle.CN, CN);
        return x500NameBuilder.build();
    }

    /**
     * 生成 keyPair
     *
     * @param seed 随机数种子
     * @return keyPair
     * @throws Exception 异常
     */
    public static KeyPair generateKeyPair(int seed) throws Exception {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048, new SecureRandom(new byte[seed]));
        return rsa.generateKeyPair();
    }

    /**
     * 生成证书
     *
     * @param keyPair  {@link #generateKeyPair(int)}
     * @param x500Name {@link #generateSubject(String, String, String, String, String, String)}
     * @return 证书
     * @throws Exception 异常
     */
    public static Certificate generateCert(KeyPair keyPair, X500Name x500Name) throws Exception {
        long notBefore = System.currentTimeMillis();
        long notAfter = notBefore + 365L * 24 * 60 * 60 * 1000;
        Date notBeforeTime = new Date(notBefore);
        Date notAfterTime = new Date(notAfter);
        SubjectPublicKeyInfo subjectPublicKeyInfo = SubjectPublicKeyInfo.getInstance(keyPair.getPublic().getEncoded());
        BigInteger serail = BigInteger.probablePrime(32, new Random());
        X509v3CertificateBuilder builder = new X509v3CertificateBuilder
                (x500Name, serail, notBeforeTime, notAfterTime, x500Name, subjectPublicKeyInfo);
        X509CertificateHolder holder = builder.build(new JcaContentSignerBuilder("SHA1withRSA")
                .build(keyPair.getPrivate()));
        return new JcaX509CertificateConverter().setProvider("BC").getCertificate(holder);
    }

    public static KeyStore loadKeyStore(final String jksPath, final String password) throws Exception {
        File keyStoreFile = new File(jksPath);
        KeyStore keystore;
        if (!keyStoreFile.exists()) {
            throw new FileNotFoundException(keyStoreFile.getPath());
        } else {
            keystore = KeyStore.getInstance(keyStoreFile, password.toCharArray());
        }
        return keystore;
    }

    /**
     * 加载pem证书文件
     *
     * @param pemFilePath 路径
     * @return keyPair
     * @throws IOException 异常
     */
    public static KeyPair loadPEMKey(String pemFilePath) throws IOException {
        KeyPair keyPair = null;
        // 用于加载PEM文件的PEMParser
        try (PEMParser pemParser = new PEMParser(new FileReader(pemFilePath))) {
            // 解析PEM数据
            Object object = pemParser.readObject();
            if (object instanceof PEMKeyPair pemKeyPair) {
                // 使用JcaPEMKeyConverter转换成Java的KeyPair对象
                JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
                keyPair = converter.getKeyPair(pemKeyPair);
            }
        }
        return keyPair;
    }

    /**
     * 加载pem证书文件
     *
     * @param pemFilePath 路径
     * @return Object[]  0-X509CertificateHolder 1-Certificate
     * @throws IOException 异常
     */
    public static Object[] loadPEMCert(String pemFilePath) throws IOException, CertificateException {
        Object[] obj = new Object[2];
        // 用于加载PEM文件的PEMParser
        try (PEMParser pemParser = new PEMParser(new FileReader(pemFilePath))) {
            // 解析PEM数据
            Object object = pemParser.readObject();
            if (object instanceof X509CertificateHolder certificateHolder) {
                obj[0] = certificateHolder;
                obj[1] = new JcaX509CertificateConverter().setProvider("BC").getCertificate(certificateHolder);
            }
        }
        return obj;
    }

    /**
     * PEMKeyPair 转换为 KeyPair
     *
     * @param pemKeyPair PEMKeyPair
     * @return KeyPair
     * @throws PEMException 异常
     */
    public static KeyPair pemKeyPairToKeyPair(PEMKeyPair pemKeyPair) throws PEMException {
        // 使用JcaPEMKeyConverter转换成Java的KeyPair对象
        JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");
        return converter.getKeyPair(pemKeyPair);
    }

    /**
     * 添加keyEntry到keyStore
     *
     * @param keyStore keyStore
     * @param keyPwd   keyPwd
     * @param keyPair  keyPair {@link #loadPEMKey(String)}
     * @param alias    alias
     * @throws KeyStoreException 异常
     */
    public static void addStoreKeyEntry(KeyStore keyStore, String keyPwd, KeyPair keyPair, String alias) throws KeyStoreException {
        keyStore.setKeyEntry(alias, keyPair.getPrivate(), keyPwd.toCharArray(), null);
    }
}

package com.devsss.onlyone.core;

import com.devsss.onlyone.core.net.Client;
import com.devsss.onlyone.core.net.Server;
import com.devsss.onlyone.core.protocol.ssl.SslServer;
import com.devsss.onlyone.core.util.KeyStoreUtils;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.util.ArrayList;

@Slf4j
public class CoreTest {

    @Test
    public void testInt() {
        StringBuilder sb = new StringBuilder();
        ArrayList<Integer> list = new ArrayList<>();
        list.add(89);
        list.forEach(i -> sb.append((char) i.intValue()));
        log.info("89 is : {}", sb);
    }

    @Test
    public void testServer() throws IOException {
        Server server = new Server(9999);
        server.listen();
    }

    @Test
    public void testClient() throws IOException {
        Client client = new Client("id", "licence", "localhost", 9999);
        client.getProxyList().put("test1.abcd", "127.0.0.1:8080");
        client.getProxyList().put("test2.abcd", "127.0.0.1:8888");
        client.connect();
    }

    @Test
    public void testKeyStore() throws Exception {
        String jksPath = "E:\\myProject\\onlyone\\core\\src\\main\\resources\\server.jks";
        File keyStoreFile = new File(jksPath);
        X500Name x500Name = KeyStoreUtils.generateSubject("CN", "chengdu", "chengdu",
                "test", "test", "test2.abcd");
        KeyPair keyPair = KeyStoreUtils.generateKeyPair(666);
        Certificate cert = KeyStoreUtils.generateCert(keyPair, x500Name);
        KeyStore keyStore = KeyStoreUtils.loadKeyStore(jksPath, SslServer.keyStorePassword);
        keyStore.setKeyEntry("test2.abcd", keyPair.getPrivate(), SslServer.keyPassword.toCharArray(),
                new Certificate[]{cert});
        keyStore.store(new FileOutputStream(keyStoreFile), SslServer.keyStorePassword.toCharArray());
        log.info("加载完成：{}", keyStore.size());
    }

    @Test
    public void testLoadKeyStore() throws Exception {
        String jksPath = "E:\\myProject\\onlyone\\core\\src\\main\\resources\\server.jks";
        String password = "storepass";
        KeyStore keyStore = KeyStoreUtils.loadKeyStore(jksPath, password);
        keyStore.aliases().asIterator().forEachRemaining(s -> {
            log.debug("aliase:{}", s);
        });
    }
}

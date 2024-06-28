package com.devsss.onlyone;

import com.devsss.onlyone.core.net.Client;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;

@Slf4j
@Getter
public class ClientApp {

    Client client;

    public static void main(String[] args) throws IOException {
        ClientApp clientApp = new ClientApp();
        clientApp.start();
    }

    @SuppressWarnings("unchecked")
    public void start() throws IOException {
        URL resource = this.getClass().getClassLoader().getResource("application.yml");
        Yaml yaml = new Yaml();
        Map<String, Object> load = yaml.load(resource.openStream());
        Map<String, Object> onlyone = (Map<String, Object>) load.get("onlyone");
        Map<String, Object> server = (Map<String, Object>) onlyone.get("server");
        Map<String, Object> client = (Map<String, Object>) onlyone.get("client");
        String serverHost = (String) server.get("host");
        int serverPort = (int) server.get("port");
        String id = (String) client.get("id");
        String licence = (String) client.get("licence");
        String msgKey = (String) client.get("msgKey");
        int retry = (int) client.get("retry");
        Object proxy = client.get("proxy");
        this.client = new Client(id, licence, serverHost, serverPort);
        this.client.setMsgKey(msgKey);
        if (proxy instanceof List<?> proxyList) {
            for (Object o : proxyList) {
                Map<String, Object> proxyMap = (Map<String, Object>) o;
                String url = (String) proxyMap.get("url");
                String ip = (String) proxyMap.get("ip");
                int port = (int) proxyMap.get("port");
                this.client.getProxyList().put(url, ip + ":" + port);
            }
        } else if (proxy instanceof Map<?, ?> proxyMap) {
            String url = (String) proxyMap.get("url");
            String ip = (String) proxyMap.get("ip");
            int port = (int) proxyMap.get("port");
            this.client.getProxyList().put(url, ip + ":" + port);
        }
        if (!this.client.getProxyList().isEmpty()) {
            for (; ; ) {
                try {
                    log.info("开始建立连接");
                    this.client.connect();
                } catch (Exception e) {
                    log.warn("onlyOneClient连接失败: {}", e.getMessage());
                } finally {
                    log.info("连接出现问题，{}秒后重试", retry);
                    try {
                        Thread.sleep(retry * 1000L);
                    } catch (InterruptedException e) {
                        log.error(e.getMessage());
                    }
                }
            }
        } else {
            log.warn("没有需要代理的链接，执行结束");
        }

    }
}
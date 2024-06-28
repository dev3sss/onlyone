package com.devsss.onlyone.server.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "onlyone.client")
public class ProxyListConfig {

    private List<ProxyClient> proxy;

    public void setProxy(List<ProxyClient> proxy) {
        this.proxy = proxy;
        log.info("proxy config: {}", proxy);
    }

    @Data
    public static class ProxyClient{
        String url;
        String ip;
        int port;
    }
}

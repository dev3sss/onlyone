package com.devsss.onlyone.server.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Data
@Configuration
public class ClientConfig {

    @Value("${onlyone.server.host}")
    private String host;

    @Value("${onlyone.server.port}")
    private int port;

    @Value("${onlyone.client.id}")
    private String id;

    @Value("${onlyone.client.licence}")
    private String licence;

    @Value("${onlyone.client.msgKey}")
    private String msgKey;

    private List<ProxyListConfig.ProxyClient> proxy;

    public ClientConfig(ProxyListConfig proxyListConfig) {
        this.proxy = proxyListConfig.getProxy();
    }
}

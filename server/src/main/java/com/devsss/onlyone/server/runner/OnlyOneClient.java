package com.devsss.onlyone.server.runner;

import com.devsss.onlyone.core.net.Client;
import com.devsss.onlyone.server.config.ClientConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Getter
@Slf4j
@Component
public class OnlyOneClient {

    Client client;

    ClientConfig clientConfig;

    public OnlyOneClient(ClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    public void run() throws Exception {
        client = new Client(clientConfig.getId(), clientConfig.getLicence(), clientConfig.getHost(), clientConfig.getPort());
        clientConfig.getProxy().forEach(p -> {
            log.debug("添加代理: {} - {}", p.getUrl(), p.getIp() + ":" + p.getPort());
            client.getProxyList().put(p.getUrl(), p.getIp() + ":" + p.getPort());
        });
        client.setMsgKey(clientConfig.getMsgKey());
        log.debug("OnlyOneClient启动");
        client.connect();
    }
}

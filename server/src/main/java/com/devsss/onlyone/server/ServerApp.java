package com.devsss.onlyone.server;

import com.devsss.onlyone.server.runner.OnlyOneClient;
import com.devsss.onlyone.server.runner.OnlyOneServer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.stereotype.Component;

import java.util.TimeZone;

@Slf4j
@Component
@EnableWebSecurity
@EnableJpaAuditing
@SpringBootApplication
public class ServerApp implements CommandLineRunner {

    OnlyOneServer onlyOneServer;

    OnlyOneClient onlyOneClient;

    public ServerApp(OnlyOneServer server, OnlyOneClient client) {
        this.onlyOneClient = client;
        this.onlyOneServer = server;
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+8"));
        SpringApplication.run(ServerApp.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Thread.ofVirtual().start(() -> {
            for (; ; ) {
                try {
                    onlyOneClient.run();
                } catch (Exception e) {
                    log.debug("onlyOneClient连接失败: {}", e.getMessage());
                } finally {
                    log.info("连接出现问题，5秒后重试");
                    try {
                        Thread.sleep(5 * 1000);
                    } catch (InterruptedException e) {
                        log.error(e.getMessage());
                    }
                }
            }
        });
        onlyOneServer.run();
    }
}

package com.devsss.onlyone.server.config;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
public class ServerConfig {

    @Value("${server.port}")
    private int serverManagerPort;

    @Value("${onlyone.server.port}")
    private int port;

    @Value("${onlyone.server.host}")
    private String host;

    /**
     * 存放jks的路径
     */
    @Value("${onlyone.server.jks}")
    private String jks;

    @Value("${onlyone.server.filepath}")
    private String filepath;
}

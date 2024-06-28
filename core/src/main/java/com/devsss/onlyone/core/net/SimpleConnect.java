package com.devsss.onlyone.core.net;


import lombok.extern.slf4j.Slf4j;

import java.net.Socket;
import java.util.function.Supplier;

@Slf4j
public class SimpleConnect extends AbstractConnect{

    private final Supplier<Boolean> start;

    public SimpleConnect(Socket socket, Supplier<Boolean> start) {
        super(socket);
        this.start = start;
    }

    @Override
    public synchronized boolean start() {
        if (start == null ) {
            log.warn("没有设置start方法");
            return false;
        }
        return this.start.get();
    }
}

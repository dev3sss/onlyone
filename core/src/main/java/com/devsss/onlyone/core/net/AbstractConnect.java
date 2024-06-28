package com.devsss.onlyone.core.net;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

@Slf4j
@AllArgsConstructor
public abstract class AbstractConnect implements Connect{

    private Socket socket;

    @Override
    public InputStream getInputStream() throws IOException {
        return socket.getInputStream();
    }

    @Override
    public OutputStream getOutStream() throws IOException {
        return socket.getOutputStream();
    }

    @Override
    public boolean stop() {
        try {
            socket.close();
            return true;
        } catch (IOException e) {
            log.error("连接关闭失败: {}",e.getMessage());
            return false;
        }
    }

    /**
     * @name 写入数据
     * @description  写入数据并flush
     * @param msg 消息内容
     * @throws IOException 异常
     */
    @Override
    public void write(byte[] msg) throws IOException {
        getOutStream().write(msg);
        getOutStream().flush();
    }
}

package com.devsss.onlyone.core.protocol.http;

import com.devsss.onlyone.core.net.MsgRepeater;
import com.devsss.onlyone.core.util.ByteUtils;
import com.devsss.onlyone.core.util.ProtocolUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

@Slf4j
public class HttpMsgRepeatHandler implements MsgRepeater {

    @Setter
    private Consumer<byte[]> responseConsumer;

    private volatile boolean chunked;

    private Consumer<Boolean> completed = ok -> {};

    @Override
    public final void acceptResponse(byte[] response) throws IOException {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(response);
        acceptResponse(inputStream);
    }

    @Override
    public void acceptResponse(InputStream inputStream) throws IOException {
        if (!chunked) {
            // 第一次进来，看响应数据是否是分块的
            HttpResponse thirdResponse = ProtocolUtils.decodeHttpResponseHeader(inputStream);
            if (thirdResponse.isChunked()) {
                chunked = true;
                this.completed.accept(false);
                // 发送头部信息
                sendResponse(thirdResponse.getHeaderByte());
            } else {
                this.completed.accept(true);
                byte[] response = ByteUtils.join(thirdResponse.getHeaderByte(), inputStream.readNBytes(thirdResponse.getContentLength()));
                sendResponse(response);
                return;
            }
        }
        // 分块的处理逻辑,来一次发一次
        ProtocolUtils.getHttpChunkedData(inputStream,(bytes, ok) -> {
            try {
                sendResponse(bytes);
                this.completed.accept(ok);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void sendResponse(byte[] response) throws IOException {
        if (responseConsumer != null) {
            responseConsumer.accept(response);
        }
    }

    @Override
    public void onCompleted(Consumer<Boolean> completed) {
        this.completed = completed;
    }
}

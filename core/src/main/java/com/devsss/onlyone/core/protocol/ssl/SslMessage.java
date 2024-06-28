package com.devsss.onlyone.core.protocol.ssl;

import com.devsss.onlyone.core.util.ByteUtils;
import lombok.Data;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

@Data
public class SslMessage {
    ContentType contentType;
    // 主版本
    int zbb;
    // 次版本
    int cbb;
    byte[] body;

    public InputStream getBodyInputStream() {
        return new ByteArrayInputStream(getBody());
    }

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // 内容类型1、主要版本1、次要版本1、数据包长度2
        out.write(ByteUtils.getBytes8(contentType.getValue()));
        out.write(ByteUtils.getBytes8(zbb));
        out.write(ByteUtils.getBytes8(cbb));
        out.write(ByteUtils.getBytes16(body.length));
        out.write(body);
        return out.toByteArray();
    }
}

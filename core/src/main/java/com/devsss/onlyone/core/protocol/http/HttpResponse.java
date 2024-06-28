package com.devsss.onlyone.core.protocol.http;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class HttpResponse {
    Map<String, String> attribute = new HashMap<>();
    Integer contentLength;
    String line1;
    // Transfer-Encoding
    boolean chunked;
    // 存放完整的头部信息，防止拼接http头部时顺序改变
    byte[] headerByte;
    byte[] body;
}

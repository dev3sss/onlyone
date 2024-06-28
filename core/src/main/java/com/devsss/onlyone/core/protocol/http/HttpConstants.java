package com.devsss.onlyone.core.protocol.http;

public class HttpConstants {

    public static byte[] NotFound404 = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\n\r\n".getBytes();

    public static byte[] ValidateMsg = "GET /validatemsgvalidate HTTP/1.1\r\n\r\n".getBytes();
}

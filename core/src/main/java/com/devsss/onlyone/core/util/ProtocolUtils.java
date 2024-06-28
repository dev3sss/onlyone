package com.devsss.onlyone.core.util;

import com.devsss.onlyone.core.protocol.DetermineProtocolResult;
import com.devsss.onlyone.core.protocol.ProtocolType;
import com.devsss.onlyone.core.protocol.http.HttpRequest;
import com.devsss.onlyone.core.protocol.http.HttpResponse;
import com.devsss.onlyone.core.protocol.internal.InternalProtocolConstants;
import com.devsss.onlyone.core.protocol.ssl.ContentType;
import com.devsss.onlyone.core.protocol.ssl.SslMessage;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BiConsumer;

@Slf4j
public class ProtocolUtils {

    /**
     * 读取一行记录（以 \n 分割）
     *
     * @param inputStream 输入流
     * @return 返回一行内容，含 \n
     */
    public static ArrayList<Integer> readLine(InputStream inputStream) {
        int n;
        ArrayList<Integer> line = new ArrayList<>();
        while (true) {
            try {
                if ((n = inputStream.read()) == -1) return new ArrayList<>();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            line.add(n);
            char c = (char) n;
            if (c == '\n') {
                break;
            }
        }
        return line;
    }

    public static String readLineStr(InputStream inputStream) {
        int n;
        StringBuilder line = new StringBuilder();
        while (true) {
            try {
                if ((n = inputStream.read()) == -1) return null;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            char c = (char) n;
            line.append(c);
            if (c == '\n') {
                break;
            }
        }
        return line.toString();
    }

    /**
     * 判断协议类型, http和内部协议使用了第一行数据, tls协议使用了第一个byte
     *
     * @return 协议类型
     */
    public static DetermineProtocolResult determineProtocolType(InputStream inputStream) {
        final DetermineProtocolResult result = new DetermineProtocolResult();
        int n;
        ArrayList<Integer> line = new ArrayList<>();
        // 读取第一行记录进行判断
        while (true) {
            try {
                if ((n = inputStream.read()) == -1) {
                    result.setProtocolType(ProtocolType.OTHER);
                    return result;
                }
            } catch (IOException e) {
                result.setProtocolType(ProtocolType.OTHER);
                return result;
            }
            line.add(n);
            if (line.size() == 1 && n >= 20 && n <= 23) {
                // 第一个数字在20-23之间，认为是tls协议
                result.setProtocolType(ProtocolType.TLS);
                result.setUsedData(line);
                return result;
            }
            char c = (char) n;
            if (c == '\n') {
                break;
            }
        }
        // 将第一行数据转换为字符串，用于判断是否内部协议和http协议
        StringBuilder lineSb = new StringBuilder();
        line.forEach(i -> lineSb.append((char) i.intValue()));
        String lineStr = lineSb.toString();
        if (lineStr.startsWith(InternalProtocolConstants.INTERNAL_PROTOCOL_HEADER_PREFIX)) {
            // 如果第一行以 @o_o@ 开头，认为是内部协议
            result.setProtocolType(ProtocolType.INTERNAL);
            result.setUsedData(line);
            result.setUsedDataStr(lineStr);
            return result;
        }
        String[] s = lineStr.replace("\r\n", "").split(" ");
        if (s.length == 3 && s[2].startsWith("HTTP")) {
            // http协议
            result.setProtocolType(ProtocolType.HTTP);
            result.setUsedData(line);
            result.setUsedDataStr(lineStr);
            return result;
        }
        return result;
    }

    /**
     * 对称加密消息
     *
     * @param msg 明文
     * @param key 加密使用的key
     * @return 密文
     */
    public static byte[] encInternalMsgBody(byte[] msg, String key) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            byte[] keyBytes = expandLen(key.getBytes(StandardCharsets.UTF_8), 16);
            SecretKeySpec spec = new SecretKeySpec(keyBytes, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, spec);
            return cipher.doFinal(msg);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException |
                 BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 解密消息
     *
     * @param encMsg 密文
     * @param key    解密用的key
     * @return 明文
     */
    public static byte[] decInternalMsgBody(byte[] encMsg, String key) {
        try {
            Cipher cipher = Cipher.getInstance("AES");
            byte[] keyBytes = expandLen(key.getBytes(StandardCharsets.UTF_8), 16);
            SecretKeySpec spec = new SecretKeySpec(keyBytes, "AES");
            cipher.init(Cipher.DECRYPT_MODE, spec);
            return cipher.doFinal(encMsg);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException |
                 BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 将指定数组使用复制的方式扩展到指定长度
     *
     * @param b 需扩展数组
     * @param l 需扩展到的长度
     * @return 结果
     */
    public static byte[] expandLen(byte[] b, int l) {
        byte[] e = b;
        while (e.length < l) {
            e = ByteUtils.join(e, e);
        }
        return Arrays.copyOf(e, l);
    }

    /**
     * 解析http响应消息
     *
     * @param inputStream 输入流
     * @return httpResponse
     */
    public static HttpResponse decodeHttpResponseHeader(InputStream inputStream) {
        HttpResponse response = new HttpResponse();
        StringBuilder headerSb = new StringBuilder();
        String lineStr = readLineStr(inputStream);
        if (lineStr == null) {
            throw new RuntimeException("连接已断开");
        }
        headerSb.append(lineStr);
        response.setLine1(lineStr.replace("\r\n", ""));
        // 开始读取头部信息
        do {
            lineStr = readLineStr(inputStream);
            if (lineStr == null) {
                throw new RuntimeException("连接已断开");
            }
            headerSb.append(lineStr);
            if (lineStr.contains(":")) {
                String[] s1 = lineStr.split(": ");
                if (s1.length == 2) {
                    String s11 = s1[1].replace("\r\n", "");
                    if (s1[0].equals("Content-Length")) {
                        response.setContentLength(Integer.parseInt(s11));
                    }
                    if (s1[0].equals("Transfer-Encoding") && "chunked".equals(s11)) {
                        response.setChunked(true);
                    }
                    response.getAttribute().put(s1[0], s11);
                }
            }
        } while (!lineStr.equals("\r\n"));
        response.setHeaderByte(headerSb.toString().getBytes());
        return response;
    }

    /**
     * 解析http请求消息
     *
     * @param inputStream 输入流
     * @param line1       第一行内容(含\r\n)，为空时从inputStream读取
     * @return HttpRequest
     */
    public static HttpRequest decodeHttpRequestHeader(InputStream inputStream, String line1) {
        HttpRequest request = new HttpRequest();
        StringBuilder headerSb = new StringBuilder();
        String lineStr;
        if (line1 == null) {
            lineStr = readLineStr(inputStream);
            if (lineStr == null) {
                throw new RuntimeException("连接已断开");
            }
        } else {
            lineStr = line1;
        }
        request.setLine1(line1);
        headerSb.append(lineStr);
        // 开始读取头部信息
        do {
            lineStr = readLineStr(inputStream);
            if (lineStr == null) {
                throw new RuntimeException("连接已断开");
            }
            headerSb.append(lineStr);
            if (lineStr.contains(":")) {
                String[] s1 = lineStr.split(": ");
                if (s1.length == 2) {
                    String s11 = s1[1].replace("\r\n", "");
                    if (s1[0].equals("Content-Length")) {
                        request.setContentLength(Integer.parseInt(s11));
                    }
                    if (s1[0].equals("Host")) {
                        String[] host = s11.split(":");
                        request.setHostName(host[0]);
                        request.setPort(host[1]);
                    }
                    request.getAttribute().put(s1[0], s11);
                }
            }
        } while (!lineStr.equals("\r\n"));
        request.setHeaderByte(headerSb.toString().getBytes());
        return request;
    }

    /**
     * 获取http分块信息
     *
     * @param inputStream  输入流
     * @param sendResponse 入参为分块信息对应的字节及是否已经结束的标识
     * @throws IOException 异常
     */
    public static void getHttpChunkedData(InputStream inputStream, BiConsumer<byte[], Boolean> sendResponse) throws IOException {
        byte[] end = "0\r\n\r\n".getBytes();
        // 分块长度+内容，遇到0+空行时表示结束
        while (true) {
            String lenStr = readLineStr(inputStream);
            if (lenStr == null) {
                // 输入流结束了
                // 当前场景是：客户端循环从第三方读取信息，是阻塞读，但服务端是将字节数组转换为输入流，存在结束的情况
                break;
            }
            // 去掉\r\n
            lenStr = lenStr.replace("\r\n", "");
            int len = Integer.parseInt(lenStr, 16);
            if (len == 0) {
                // 把空行读掉
                readLineStr(inputStream);
                sendResponse.accept(end, true);
                break;
            } else {
                byte[] bytes = inputStream.readNBytes(len);
                // 把回车换行符读掉
                readLineStr(inputStream);
                byte[] head = lenStr.getBytes();
                byte[] rn = "\r\n".getBytes();
                byte[] bodyBytes = ByteUtils.join(head, rn, bytes, rn);
                sendResponse.accept(bodyBytes, false);
            }
        }
    }

    /**
     * 处理tls请求
     *
     * @param inputStream 输入流
     * @param tlsProtocol 内容类型，为空时在输入流中读取
     * @return ssl消息对象，为空时说明连接已断开
     */
    public static SslMessage decodeTls(InputStream inputStream, Integer tlsProtocol) throws IOException {
        SslMessage message = new SslMessage();
        // 内容类型1、主要版本1、次要版本1、数据包长度2
        int nrnx;
        if (tlsProtocol == null) {
            nrnx = inputStream.read();
            if (nrnx == -1) {
                // 为-1说明链接被关闭了
                return null;
            }
        } else {
            nrnx = tlsProtocol;
        }
        int zybb = inputStream.read();
        int cybb = inputStream.read();
        int sjbcd = ByteUtils.getInt16(inputStream.readNBytes(2));
        // 读取消息体
        byte[] bodyBytes = inputStream.readNBytes(sjbcd);
        message.setZbb(zybb);
        message.setCbb(cybb);
        message.setBody(bodyBytes);
        // 握手协议(handshake): 22
        // 警告协议(alert): 21
        // 改变密码格式协议(change_cipher_spec): 20
        // 应用数据协议(application_data): 23
        switch (nrnx) {
            case 20:
                message.setContentType(ContentType.CHANGE_CIPHER_SPEC);
                break;
            case 21:
                message.setContentType(ContentType.ALERT);
                break;
            case 22:
                message.setContentType(ContentType.HANDSHAKE);
                break;
            case 23:
                message.setContentType(ContentType.APPLICATION_DATA);
                break;
            default:
                throw new RuntimeException("未知的tls内容类型");
        }
        return message;
    }
}

package com.devsss.onlyone.core.protocol.ssl;

import com.devsss.onlyone.core.util.ByteUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Data
@AllArgsConstructor
public class HandshakeContent {
    HandshakeType handshakeType; // 1byte
    int length; // 3bytes
    byte[] content;

    public HandshakeContent(InputStream inputStream) throws IOException {
        byte[] type = inputStream.readNBytes(1);
        this.handshakeType = HandshakeType.getByValue(ByteUtils.getInt8(type));
        this.length = ByteUtils.getInt24(inputStream.readNBytes(3));
        this.content = inputStream.readNBytes(this.length);
        log.debug("握手协议类型: {}  内容长度: {}", handshakeType.name(), length);
    }

    public byte[] toBytes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(ByteUtils.getBytes8(handshakeType.getValue()));
        if (content == null) {
            out.write(ByteUtils.getBytes24(0));
        } else {
            out.write(ByteUtils.getBytes24(content.length));
            out.write(content);
        }
        return out.toByteArray();
    }

    @NoArgsConstructor
    @Data
    public static class HelloMessage {
        int protocolVersion; // 2字节
        byte[] randomTimestamp; // 4bytes,标准的UNIX 32位时间格式，表示距离1970年1月1号的秒数
        byte[] randomBytes; // 28bytes,通过SecureRandom产生
        byte[] sessionId; // 可为空，长度不固定,编码方式采用长度＋内容,用1byte表示长度，读取时先读取长度，然后根据长度读取内容
        List<byte[]> cipherSuites; //采用长度＋内容,用2bytes表示长度，每个CipherSuite占用2bytes
        byte[] compressionMethod; // 压缩方法，长度＋内容,用1byte表示长度
        byte[] extensions; // 长度＋内容,用2bytes表示长度

        public HelloMessage(byte[] data) throws IOException {
            ByteArrayInputStream in = new ByteArrayInputStream(data);
            this.protocolVersion = ByteUtils.getInt16(in.readNBytes(2));
            this.randomTimestamp = in.readNBytes(4);
            this.randomBytes = in.readNBytes(28);
            int sessionLen = ByteUtils.getInt8(in.readNBytes(1));
            if (sessionLen > 0) {
                this.sessionId = in.readNBytes(sessionLen);
            } else {
                this.sessionId = null;
            }
            int cipherSuitesLen = ByteUtils.getInt16(in.readNBytes(2));
            // 每个cipherSuite占2个字节，计算出list大小
            int cipherSuiteSize = cipherSuitesLen >> 1;
            this.cipherSuites = new ArrayList<>();
            for (int i = 0; i < cipherSuiteSize; i++) {
                this.cipherSuites.add(in.readNBytes(2));
            }
            int methodLen = ByteUtils.getInt8(in.readNBytes(1));
            this.compressionMethod = in.readNBytes(methodLen);
            int etLen = ByteUtils.getInt16(in.readNBytes(2));
            this.extensions = in.readNBytes(etLen);
        }

        public List<Extension> getExtensionList() throws IOException {
            // 2字节长度加内容
            ByteArrayInputStream inputStream = new ByteArrayInputStream(extensions);
            final List<Extension> extensionList = new ArrayList<>();
            int readLen = 0;
            while (readLen < extensions.length) {
                String type = b2toHexStr(inputStream.readNBytes(2));
                int l = ByteUtils.getInt16(inputStream.readNBytes(2));
                byte[] content = inputStream.readNBytes(l);
                Extension extension = new Extension(type,l,content);
                extensionList.add(extension);
                readLen += l + 2 + 2;
            }
            return extensionList;
        }

        private String b2toHexStr(byte[] b2) {
            return Integer.toHexString(ByteUtils.getInt16(b2));
        }
    }
}

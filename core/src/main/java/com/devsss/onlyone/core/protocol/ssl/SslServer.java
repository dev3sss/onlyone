package com.devsss.onlyone.core.protocol.ssl;

import com.devsss.onlyone.core.protocol.DetermineProtocolResult;
import com.devsss.onlyone.core.protocol.ProtocolType;
import com.devsss.onlyone.core.protocol.http.HttpRequest;
import com.devsss.onlyone.core.util.KeyStoreUtils;
import com.devsss.onlyone.core.util.ProtocolUtils;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Consumer;

@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class SslServer extends SslPeer {

    public static String kmsFile = "./src/main/resources/server.jks";
    public static String trustFile = "./src/main/resources/trustedCerts.jks";
    public static String keyStorePassword = "storepass";
    public static String keyPassword = "keypass";
    public static volatile KeyStore keyStore = null;
    private static TrustManager[] tms;
    private static KeyManager[] kms;

    private SSLContext context;
    private SSLEngine sslEngine;
    private int port;
    private String host;

    private final RepeaterByteChannel clientMsgChannel = new RepeaterByteChannel(16 * 1024, false);

    private volatile boolean doneHandshake = false;

    public SslServer(String protocol) throws Exception {
        context = SSLContext.getInstance(Objects.requireNonNullElse(protocol, "TLS"));
        synchronized ("SslServerInIt") {
            if ( keyStore == null) {
                try {
                    keyStore = KeyStoreUtils.loadKeyStore(kmsFile, keyStorePassword);
                    tms = createTrustManagers(trustFile, keyStorePassword);
                    kms = createKeyManagers(keyStore, keyPassword, null);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void initSslEngine(String host, int port) throws Exception {
        if (host == null || host.isEmpty()) {
            context.init(kms, tms, new SecureRandom());
            this.sslEngine = context.createSSLEngine();
        } else {
            this.host = host;
            this.port = port;
            KeyManager[] _kms = createKeyManagers(keyStore, keyPassword, Collections.singletonList(host));
            context.init(_kms, tms, new SecureRandom());
            this.sslEngine = context.createSSLEngine(host, port);
        }
        sslEngine.setUseClientMode(false);
        SSLSession dummySession = sslEngine.getSession();
        myAppData = ByteBuffer.allocate(dummySession.getApplicationBufferSize());
        myNetData = ByteBuffer.allocate(dummySession.getPacketBufferSize());
        peerAppData = ByteBuffer.allocate(dummySession.getApplicationBufferSize());
        peerNetData = ByteBuffer.allocate(dummySession.getPacketBufferSize());
        dummySession.invalidate();
    }


    /**
     * Will be called by the selector when the specific socket channel has data to be read.
     * As soon as the server reads these data, it will call the {@link #watchUnwrap(byte[])}
     *
     * @param byteChannel - the transport link used between the two peers.
     * @param engine      - the engine used for encryption/decryption of the data exchanged between the two peers.
     * @throws IOException if an I/O error occurs to the socket channel.
     */
    @Override
    protected void read(ByteChannel byteChannel, SSLEngine engine) throws IOException {
        peerNetData.clear();
        int bytesRead = byteChannel.read(peerNetData);
        if (bytesRead > 0) {
            peerNetData.flip();
            while (peerNetData.hasRemaining()) {
                peerAppData.clear();
                SSLEngineResult result = engine.unwrap(peerNetData, peerAppData);
                switch (result.getStatus()) {
                    case OK:
                        peerAppData.flip();
                        byte[] data = new byte[peerAppData.remaining()];
                        peerAppData.get(data, 0, peerAppData.remaining());
                        watchUnwrap(data);
                        break;
                    case BUFFER_OVERFLOW:
                        peerAppData = enlargeApplicationBuffer(engine, peerAppData);
                        break;
                    case BUFFER_UNDERFLOW:
                        peerNetData = handleBufferUnderflow(engine, peerNetData);
                        break;
                    case CLOSED:
                        closeConnection(byteChannel, engine);
                        return;
                    default:
                        throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                }
            }

//            write(byteChannel, engine, "Hello! I am your server!");

        } else if (bytesRead < 0) {
            handleEndOfStream(byteChannel, engine);
        }
    }

    /**
     * Will send a message back to a client.
     *
     * @param message - the message to be sent.
     * @throws IOException if an I/O error occurs to the socket channel.
     */
    @Override
    protected void write(ByteChannel byteChannel, SSLEngine engine, byte[] message) throws IOException {
        myAppData.clear();
        // 确定每个块的大小，预留一部分给tls协议
        int maxCapacity = myAppData.capacity() - 1024;
        // 可能存在容量不够，需要使用list
        ByteBuffer[] appDataList;
        if (maxCapacity < message.length) {
            int len = Math.ceilDiv(message.length, maxCapacity);
            appDataList = new ByteBuffer[len];
            for (int i = 0; i < len; i++) {
                byte[] bytes = Arrays.copyOfRange(message, i * maxCapacity,
                        (i + 1) * maxCapacity);
                ByteBuffer appData = ByteBuffer.allocate(maxCapacity);
                appData.put(bytes);
                appData.flip();
                appDataList[i] = appData;
            }
        } else {
            myAppData.put(message);
            myAppData.flip();
            appDataList = new ByteBuffer[]{myAppData};
        }
        for (ByteBuffer appData : appDataList) {
            while (appData.hasRemaining()) {
                // The loop has a meaning for (outgoing) messages larger than 16KB.
                // Every wrap call will remove 16KB from the original message and send it to the remote peer.
                myNetData.clear();
                SSLEngineResult result = engine.wrap(appData, myNetData);
                switch (result.getStatus()) {
                    case OK:
                        myNetData.flip();
                        while (myNetData.hasRemaining()) {
                            byteChannel.write(myNetData);
                        }
                        break;
                    case BUFFER_OVERFLOW:
                        myNetData = enlargePacketBuffer(engine, myNetData);
                        break;
                    case BUFFER_UNDERFLOW:
                        throw new SSLException("Buffer underflow occured after a wrap. I don't think we should ever get here.");
                    case CLOSED:
                        closeConnection(byteChannel, engine);
                        return;
                    default:
                        throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                }
            }
        }
    }

    @Override
    protected void watchUnwrap(byte[] data) throws IOException {
        // 读取消息时将解包的数据传递过来
        clientMsgChannel.write(ByteBuffer.wrap(data));
    }

    /**
     * 将得到的响应消息写入，由{@link #repeaterMsg(OutputStream, Consumer)}完成转发
     *
     * @param message http响应消息
     * @throws IOException 异常
     */
    public void writerMsg(byte[] message) throws IOException {
        write(clientMsgChannel, sslEngine, message);
    }

    /**
     * 转发消息
     *
     * @param reqObj 请求方的stream，用于返回ssl响应消息
     * @param func   接收解包后的请求消息
     * @throws Exception 异常
     */
    public void repeaterMsg(OutputStream reqObj, Consumer<HttpRequest> func) throws Exception {
        sslEngine.beginHandshake();
        // 开一个虚拟线程处理数据的转发
        Thread.ofVirtual().start(() -> {
            boolean loadData = false;
            byte[] data = clientMsgChannel.toData();
            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            while (true) {
                if (loadData) {
                    data = clientMsgChannel.toData();
                    inputStream = new ByteArrayInputStream(data);
                }
                // 还未完成握手
                try {
                    DetermineProtocolResult protocolResult = ProtocolUtils.determineProtocolType(inputStream);

                    if (protocolResult.getProtocolType().equals(ProtocolType.OTHER)) {
                        // 说明放进inputStream的内容读完了
                        loadData = true;
                        continue;
                    } else {
                        loadData = false;
                    }
                    if (protocolResult.getProtocolType().equals(ProtocolType.TLS)) {
                        // 读取消息
                        SslMessage sslMessage = ProtocolUtils.decodeTls(inputStream, protocolResult.getUsedData().get(0));
                        // tls消息直接转发到请求端
                        if (sslMessage != null) {
                            reqObj.write(sslMessage.toBytes());
                        } else {
                            log.error("意外的出现了空sslMessage");
                        }
                    } else {
                        HttpRequest request = ProtocolUtils.decodeHttpRequestHeader(inputStream, protocolResult.getUsedDataStr());
                        if (request.getContentLength() != null && request.getContentLength() > 0) {
                            byte[] body = inputStream.readNBytes(request.getContentLength());
                            request.setBody(body);
                        }
                        func.accept(request);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        });
        doneHandshake = doHandshake(clientMsgChannel, sslEngine);
        if (doneHandshake) {
            log.debug("握手完成");
            while (true) {
                this.read(clientMsgChannel, sslEngine);
            }
        }
    }
}


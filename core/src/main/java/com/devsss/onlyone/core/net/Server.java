package com.devsss.onlyone.core.net;

import com.devsss.onlyone.core.protocol.DetermineProtocolResult;
import com.devsss.onlyone.core.protocol.ProtocolType;
import com.devsss.onlyone.core.protocol.http.HttpConstants;
import com.devsss.onlyone.core.protocol.http.HttpMsgRepeatHandler;
import com.devsss.onlyone.core.protocol.http.HttpRequest;
import com.devsss.onlyone.core.protocol.internal.Header;
import com.devsss.onlyone.core.protocol.internal.InternalMsg;
import com.devsss.onlyone.core.protocol.internal.InternalMsgHandler;
import com.devsss.onlyone.core.protocol.internal.InternalProtocolConstants;
import com.devsss.onlyone.core.protocol.ssl.Extension;
import com.devsss.onlyone.core.protocol.ssl.HandshakeContent;
import com.devsss.onlyone.core.protocol.ssl.SslMessage;
import com.devsss.onlyone.core.protocol.ssl.SslServer;
import com.devsss.onlyone.core.task.AsyncTaskQueue;
import com.devsss.onlyone.core.util.ByteUtils;
import com.devsss.onlyone.core.util.ProtocolUtils;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;

@Slf4j
public class Server {

    /**
     * 监听端口
     */
    private final int port;

    /**
     * 存放内部连接，key为对应client id
     */
    private final ConcurrentHashMap<String, InternalMsgHandler> connectMap = new ConcurrentHashMap<>();

    /**
     * 内部代理映射， key对应代理地址  value对应client id
     */
    private final ConcurrentHashMap<String, String> internalProxyMap = new ConcurrentHashMap<>();

    /**
     * 外部请求，key对应msgId  value对应响应消息转发器
     */
    private final ConcurrentHashMap<String, MsgRepeater> externalRequestMap = new ConcurrentHashMap<>();

    @Setter
    private BiFunction<String, String, String> msgKey = (id, licence) -> InternalProtocolConstants.INTERNAL_PROTOCOL_DEFAULT_MSG_KEY;

    public Server(int port) {
        this.port = port;
    }

    /**
     * 启动socket监听
     *
     * @throws IOException 异常
     */
    public void listen() throws IOException {
        try (ServerSocket socket = new ServerSocket(port)) {
            while (true) {
                Socket accept = socket.accept();
                Thread.ofVirtual().name("SocketAccept").start(() -> {
                    try {
                        SimpleConnect connect = new SimpleConnect(accept, null);
                        // 先认为是客户端主动发消息
                        // 1.读取第一行，用于判断使用的协议
                        DetermineProtocolResult result = ProtocolUtils.determineProtocolType(connect.getInputStream());
                        log.debug("有新连接进来了，连接类型为: {}", result.getProtocolType().name());
                        switch (result.getProtocolType()) {
                            case INTERNAL -> handleInternalConnect(connect, result);
                            case HTTP -> handleHttpConnect(connect, result);
                            case TLS -> handleTlsConnect(connect, result);
                            case OTHER -> {
                                connect.stop();
                                log.error("不支持的协议");
                            }
                        }
                    } catch (Exception e) {
                        log.warn(e.getMessage());
                    }
                });
            }
        }
    }

    /**
     * 处理内部连接
     *
     * @param connect 连接
     * @param result  协议判断结果
     */
    private void handleInternalConnect(SimpleConnect connect, DetermineProtocolResult result) {
        String clientId = null;
        try {
            // 解析请求头，去掉协议前缀和结尾的 \n
            String headerStr = result.getUsedDataStr().replace(InternalProtocolConstants.INTERNAL_PROTOCOL_HEADER_PREFIX, "")
                    .replace("\n", "");
            Header header = Header.stringToHeader(headerStr);
            clientId = header.getId();
            InternalMsgHandler handler = new InternalMsgHandler(connect);
            String clientMsgKey = msgKey.apply(header.getId(), header.getLicence());
            handler.setMsgKey(() -> clientMsgKey);
            // 存放代理客户端
            connectMap.put(header.getId(), handler);
            // 读取消息体，获取代理列表
            byte[] body = connect.getInputStream().readNBytes(header.getLength());
            // 解密消息
            InternalMsg beatMsg = handler.decMsg(new InternalMsg(header, body));
            if (header.getMsgType().equals(Header.MsgType.BEAT)) {
                Arrays.stream(beatMsg.getProxyList()).forEach(p -> internalProxyMap.put(p, header.getId()));
            }
            // 循环读取消息
            for (; ; ) {
                InternalMsg msg = handler.readMsg();
                switch (msg.getHeader().getMsgType()) {
                    case BEAT -> {
                        if (msg.getHeader().getLength() > 0) {
                            // 清除所有相关的代理映射
                            internalProxyMap.forEach((proxyName, id) -> {
                                if (header.getId().equals(id)) {
                                    internalProxyMap.remove(proxyName);
                                }
                            });
                            // 添加新的代理映射
                            Arrays.stream(msg.getProxyList()).forEach(p -> internalProxyMap.put(p, header.getId()));
                        }
                    }
                    case RESPONSE -> {
                        // 服务器转发请求到客户端后，客户端的响应结果需要转发到对应的请求端
                        String msgId = msg.getHeader().getMsgId();
                        log.debug("得到响应消息,msgId: {}", msgId);
                        if (externalRequestMap.containsKey(msgId)) {
                            MsgRepeater repeatHandler = externalRequestMap.get(msgId);
                            repeatHandler.acceptResponse(msg.getBody());
                        }
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            log.error("clientId: [{}] 处理内部消息发生错误: {}", clientId, e.getMessage());
        }
    }

    /**
     * 处理http连接
     *
     * @param reqConnect 连接
     * @param result     协议判断结果
     */
    private void handleHttpConnect(SimpleConnect reqConnect, DetermineProtocolResult result) throws IOException {
        HttpRequest request = ProtocolUtils.decodeHttpRequestHeader(reqConnect.getInputStream(), result.getUsedDataStr());
        if (repeatHttpMsg(request, reqConnect)) {
            String keepAlive = request.getAttribute().get("Connection");
            if ("keep-alive".equals(keepAlive)) {
                for (; ; ) {
                    HttpRequest httpRequest = ProtocolUtils.decodeHttpRequestHeader(reqConnect.getInputStream(), null);
                    if (!repeatHttpMsg(httpRequest, reqConnect)) {
                        return;
                    }
                }
            }
        }
    }

    /**
     * 转发http消息
     *
     * @param request    请求信息
     * @param reqConnect 请求方连接
     * @return 是否找到代理
     * @throws IOException 异常
     */
    private boolean repeatHttpMsg(HttpRequest request, SimpleConnect reqConnect) throws IOException {
        if (internalProxyMap.containsKey(request.getHostName())) {
            String clientId = internalProxyMap.get(request.getHostName());
            if (!connectMap.containsKey(clientId)) {
                return false;
            }
            Consumer<byte[]> responseConsumer = b -> {
                try {
                    reqConnect.write(b);
                } catch (IOException e) {
                    log.error("http响应消息发送到请求端失败: {}", e.getMessage());
                }
            };
            InternalMsgHandler client = connectMap.get(clientId);
            String msgId = request.getHostName() + Header.generateMsgId();
            // 创建转发响应消息的处理对象
            HttpMsgRepeatHandler repeatHandler = new HttpMsgRepeatHandler();
            repeatHandler.setResponseConsumer(responseConsumer);
            AsyncMsgRepeater asyncMsgRepeater = new AsyncMsgRepeater(repeatHandler, new AsyncTaskQueue());
            asyncMsgRepeater.onCompleted(ok -> {
                if (ok) {
                    externalRequestMap.remove(msgId);
                    log.debug("响应消息msgId: {} 转发完毕", msgId);
                }
            });
            externalRequestMap.put(msgId, asyncMsgRepeater);
            int msgBodyLen = request.getContentLength() == null ? 0 : request.getContentLength();
            // 发送http请求到代理客户端
            Header header = Header.builder().id(request.getHostName()).licence(ProtocolType.HTTP.name())
                    .msgType(Header.MsgType.REQUEST).msgId(msgId).length(msgBodyLen).build();
            byte[] body;
            if (msgBodyLen == 0) {
                body = request.getHeaderByte();
            } else {
                body = ByteUtils.join(request.getHeaderByte(), reqConnect.getInputStream().readNBytes(msgBodyLen));
            }
            client.writeMsg(new InternalMsg(header, body));
            return true;
        } else {
            log.warn("未找到对应的代理，hostName: {}", request.getHostName());
            reqConnect.getOutStream().write(HttpConstants.NotFound404);
            return false;
        }
    }

    /**
     * 处理tls连接
     *
     * @param connect 连接
     * @param result  协议判断结果
     */
    private void handleTlsConnect(SimpleConnect connect, DetermineProtocolResult result) throws Exception {
        SslMessage sslMessage = ProtocolUtils.decodeTls(connect.getInputStream(), result.getUsedData().get(0));
        if (sslMessage == null) {
            return;
        }
        HandshakeContent handshakeContent = new HandshakeContent(new ByteArrayInputStream(sslMessage.getBody()));
        HandshakeContent.HelloMessage helloMessage = new HandshakeContent.HelloMessage(handshakeContent.getContent());
        String reqHost = null;
        for (Extension extension : helloMessage.getExtensionList()) {
            reqHost = extension.getHostName();
            if (reqHost != null) {
                break;
            }
        }
        SslServer sslServer = new SslServer(null);
        if (SslServer.keyStore.containsAlias(reqHost)) {
            sslServer.initSslEngine(reqHost, port);
        } else {
            sslServer.initSslEngine(null, port);
            log.warn("不存在指定域名证书:{}", reqHost);
        }
        sslServer.getClientMsgChannel().fromData(sslMessage.toBytes());
        Thread.ofVirtual().start(() -> {
            try {
                sslServer.repeaterMsg(connect.getOutStream(), request -> {
                    try {
                        repeatHttpsMsg(request, bytes -> {
                            try {
                                sslServer.writerMsg(bytes);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        while (true) {
            // 读取ssl消息
            SslMessage msg = ProtocolUtils.decodeTls(connect.getInputStream(), null);
            if (msg == null) {
                return;
            }
            sslServer.getClientMsgChannel().fromData(msg.toBytes());
        }
    }

    /**
     * 转发https消息
     *
     * @param request          请求信息
     * @param responseConsumer 响应信息消费者
     * @throws IOException 异常
     */
    private void repeatHttpsMsg(HttpRequest request, Consumer<byte[]> responseConsumer) throws IOException {
        if (internalProxyMap.containsKey(request.getHostName())) {
            String clientId = internalProxyMap.get(request.getHostName());
            if (!connectMap.containsKey(clientId)) {
                return;
            }
            InternalMsgHandler client = connectMap.get(clientId);
            String msgId = request.getHostName() + Header.generateMsgId();
            // 创建转发响应消息的处理对象
            HttpMsgRepeatHandler repeatHandler = new HttpMsgRepeatHandler();
            repeatHandler.setResponseConsumer(responseConsumer);
            AsyncMsgRepeater asyncMsgRepeater = new AsyncMsgRepeater(repeatHandler, new AsyncTaskQueue());
            asyncMsgRepeater.onCompleted(ok -> {
                if (ok) {
                    externalRequestMap.remove(msgId);
                    log.debug("响应消息msgId: {} 转发完毕", msgId);
                }
            });
            externalRequestMap.put(msgId, asyncMsgRepeater);
            int msgBodyLen = request.getContentLength() == null ? 0 : request.getContentLength();
            // 发送http请求到代理客户端
            Header header = Header.builder().id(request.getHostName()).licence(ProtocolType.HTTP.name())
                    .msgType(Header.MsgType.REQUEST).msgId(msgId).length(msgBodyLen).build();
            byte[] body;
            if (msgBodyLen == 0) {
                body = request.getHeaderByte();
            } else {
                body = ByteUtils.join(request.getHeaderByte(), request.getBody());
            }
            client.writeMsg(new InternalMsg(header, body));
        } else {
            log.warn("未找到对应的代理，hostName: {}", request.getHostName());
            responseConsumer.accept(HttpConstants.NotFound404);
        }
    }

    /**
     * 获取客户端代理列表
     * @return map key:client_id  value:proxyStrList
     */
    public Map<String, List<String>> getClientProxyList() {
        final Map<String, List<String>> clientProxyList = new HashMap<>();
        internalProxyMap.forEach( (proxyStr, clientId) -> {
            if (clientProxyList.containsKey(clientId)) {
                clientProxyList.get(clientId).add(proxyStr);
            } else {
                clientProxyList.put(clientId, new ArrayList<>(Collections.singletonList(proxyStr)));
            }
        });
        return clientProxyList;
    }
}

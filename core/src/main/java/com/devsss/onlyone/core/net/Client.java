package com.devsss.onlyone.core.net;

import com.devsss.onlyone.core.protocol.ProtocolType;
import com.devsss.onlyone.core.protocol.http.HttpClientSocketPool;
import com.devsss.onlyone.core.protocol.http.HttpConstants;
import com.devsss.onlyone.core.protocol.http.HttpMsgRepeatHandler;
import com.devsss.onlyone.core.protocol.internal.Header;
import com.devsss.onlyone.core.protocol.internal.InternalMsg;
import com.devsss.onlyone.core.protocol.internal.InternalMsgHandler;
import com.devsss.onlyone.core.protocol.internal.InternalProtocolConstants;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

@Data
@Slf4j
public class Client {

    private String hostName;

    private int port;

    private InternalMsgHandler internalMsgHandler;

    /**
     * 存放代理列表，key为代理的hostName，value为实际的可访问ip:port
     */
    private ConcurrentHashMap<String, String> proxyList = new ConcurrentHashMap<>();

    private HttpClientSocketPool proxyPool = new HttpClientSocketPool();

    private String id;

    private String licence;

    // 用于加解密消息
    private String msgKey;

    public Client(String id, String licence, String hostName, int port) {
        this.hostName = hostName;
        this.port = port;
        this.id = id;
        this.licence = licence;
        this.msgKey = InternalProtocolConstants.INTERNAL_PROTOCOL_DEFAULT_MSG_KEY;
    }

    public Client(String id, String licence, String hostName, int port, String msgKey) {
        this.hostName = hostName;
        this.port = port;
        this.id = id;
        this.licence = licence;
        this.msgKey = msgKey;
    }

    public void connect() throws IOException {
        try (Socket socket = new Socket(hostName, port)) {
            Connect connect = new SimpleConnect(socket, null);
            internalMsgHandler = new InternalMsgHandler(connect);
            internalMsgHandler.setMsgKey(() -> msgKey);
            // 连接成功后发送代理列表
            internalMsgHandler.writeMsg(beatMsg());
            // 读取消息
            for (; ; ) {
                InternalMsg msg = internalMsgHandler.readMsg();
                Thread.ofVirtual().start(() -> {
                    Header header = msg.getHeader();
                    switch (header.getMsgType()) {
                        case REQUEST -> {
                            // 根据不同的协议执行对应的代理方法，目前只有http
                            if (header.getLicence().equals(ProtocolType.HTTP.name())) {
                                try {
                                    repeatHttpMsg(msg);
                                } catch (Exception e) {
                                    log.error("转发http消息失败,msgId: {} err: {}", msg.getHeader().getMsgId(), e.getMessage());
                                    try {
                                        internalMsgHandler.writeMsg(new InternalMsg(header, HttpConstants.NotFound404));
                                    } catch (IOException ex) {
                                        throw new RuntimeException(ex);
                                    }
                                }
                            }
                        }
                        case RESPONSE -> {

                        }
                    }
                });
            }
        }
    }

    private InternalMsg beatMsg() {
        Header header = Header.builder().id(id).licence(licence).msgType(Header.MsgType.BEAT).length(0).build();
        StringBuilder sb = new StringBuilder();
        proxyList.forEach((p, v) -> sb.append(p).append(" "));
        if (!sb.isEmpty()) {
            // 删除最后一个空格
            sb.deleteCharAt(sb.length() - 1);
        } else {
            log.warn("代理列表为空");
        }
        return new InternalMsg(header, sb.toString().getBytes());
    }

    /**
     * 查找对应的第三方连接
     *
     * @param hostName 第三方对外域名
     * @return 连接
     * @throws IOException 异常
     */
    private SimpleConnect findService(String hostName) throws Exception {
        if (proxyList.containsKey(hostName)) {
            return proxyPool.getConnect(proxyList.get(hostName));
        } else {
            throw new RuntimeException("未知的hostName:" + hostName);
        }
    }

    private void repeatHttpMsg(InternalMsg msg) throws Exception {
        Header header = msg.getHeader();
        SimpleConnect service = findService(header.getId());
        if (service != null) {
            // 创建转发响应消息的处理对象
            HttpMsgRepeatHandler repeatHandler = new HttpMsgRepeatHandler();
            repeatHandler.setResponseConsumer(bytes -> {
                header.setMsgType(Header.MsgType.RESPONSE);
                header.setLength(bytes.length);
                try {
                    internalMsgHandler.writeMsg(new InternalMsg(header, bytes));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
            // 请求第三方服务
            for (int x = 1; x < HttpClientSocketPool.IDLE_PERKEY + 3; x++) {
                try {
                    log.debug("msgId: {}尝试第【{}】次发送消息", msg.getHeader().getMsgId(), x);
                    // 请求
                    service.write(msg.getBody());
                    // 响应
                    repeatHandler.acceptResponse(service.getInputStream());
                    log.debug("msgId: {}第【{}】次发送消息成功", msg.getHeader().getMsgId(), x);
                    proxyPool.returnConnect(proxyList.get(header.getId()), service);
                    return;
                } catch (IOException | RuntimeException e) {
                    log.error("msgId: {}失败了, err: {}", msg.getHeader().getMsgId(), e.getMessage());
                    service.stop();
                    proxyPool.returnConnect(proxyList.get(header.getId()), service);
                    // 重新获取service
                    service = findService(header.getId());
                }
            }
            // 到这里说明转发消息失败了
            repeatHandler.acceptResponse(HttpConstants.NotFound404);
        }
    }
}

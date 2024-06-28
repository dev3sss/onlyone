package com.devsss.onlyone.core.protocol.internal;

import com.devsss.onlyone.core.net.Connect;
import com.devsss.onlyone.core.util.ProtocolUtils;
import lombok.Setter;

import java.io.IOException;
import java.util.function.Supplier;

public class InternalMsgHandler {

    private final Connect connect;

    @Setter
    private Supplier<String> msgKey = () -> InternalProtocolConstants.INTERNAL_PROTOCOL_DEFAULT_MSG_KEY;

    public InternalMsgHandler(Connect connect) {
        this.connect = connect;
    }

    /**
     * 解密数据
     *
     * @param msg 已加密消息
     * @return 已解密消息
     */
    public InternalMsg decMsg(InternalMsg msg) {
        Header header = msg.getHeader();
        byte[] bytes = msg.getBody();
        String key = msgKey.get();
        // 解密数据
        byte[] decBody = ProtocolUtils.decInternalMsgBody(bytes, key);
        header.setLength(decBody.length);
        return new InternalMsg(header, decBody);
    }

    /**
     * 加密数据
     *
     * @param msg 待加密消息
     * @return 已加密消息
     */
    public InternalMsg encMsg(InternalMsg msg) {
        Header header = msg.getHeader();
        byte[] bytes = msg.getBody();
        String key = msgKey.get();
        // 加密数据
        byte[] encBody = ProtocolUtils.encInternalMsgBody(bytes, key);
        header.setLength(encBody.length);
        return new InternalMsg(header, encBody);
    }

    /**
     * 从连接中读取数据
     *
     * @return 读取到的已解密消息
     * @throws IOException 异常
     */
    public InternalMsg readMsg() throws IOException {
        String lineStr = ProtocolUtils.readLineStr(connect.getInputStream());
        if (lineStr == null) {
            throw new RuntimeException("连接已断开");
        }
        String headerStr = lineStr.replace(InternalProtocolConstants.INTERNAL_PROTOCOL_HEADER_PREFIX, "")
                .replace("\n", "");
        Header header = Header.stringToHeader(headerStr);
        // 读取消息体
        byte[] bytes = connect.getInputStream().readNBytes(header.getLength());
        return decMsg(new InternalMsg(header, bytes));
    }

    /**
     * 发送消息
     *
     * @param msg 未加密的待发送消息
     * @throws IOException 异常
     */
    public synchronized void writeMsg(InternalMsg msg) throws IOException {
        connect.write(encMsg(msg).toMsgBytes());
    }
}

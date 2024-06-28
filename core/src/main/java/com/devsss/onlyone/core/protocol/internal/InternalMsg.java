package com.devsss.onlyone.core.protocol.internal;

import com.devsss.onlyone.core.util.ByteUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

@Data
@Slf4j
@AllArgsConstructor
public class InternalMsg {

    private Header header;

    private byte[] body;

    public byte[] toMsgBytes() {
        if (header.getLength() <= 0) {
            return ByteUtils.join(InternalProtocolConstants.INTERNAL_PROTOCOL_HEADER_PREFIX.getBytes(),
                    header.toHeaderMsg().getBytes());
        }
        return ByteUtils.join(InternalProtocolConstants.INTERNAL_PROTOCOL_HEADER_PREFIX.getBytes(),
                header.toHeaderMsg().getBytes(), body);
    }

    public String[] getProxyList() {
        if (header.getMsgType().equals(Header.MsgType.BEAT)) {
            String[] proxyList = new String(getBody()).split(" ");
            log.debug("id: {} 得到代理列表: {}", header.getId(), proxyList);
            return proxyList;
        } else {
            log.warn("在非BEAT消息中调用getProxyList");
            return null;
        }
    }

    public static InternalMsg beatMsg(String id, String licence, ArrayList<String> proxyList) {
        StringBuilder sb = new StringBuilder();
        proxyList.forEach(p -> sb.append(p).append(" "));
        sb.deleteCharAt(sb.length() - 1);
        byte[] body = sb.toString().getBytes();
        Header h = new Header(id, licence, Header.MsgType.BEAT, Header.generateMsgId(), body.length);
        return new InternalMsg(h, body);
    }
}

package com.devsss.onlyone.core.protocol.internal;

import lombok.Builder;
import lombok.Data;

import java.text.SimpleDateFormat;

@Data
@Builder
public class Header {

    /**
     * beat消息时为client_id，代理消息时为代理目标端hostName
     */
    private String id;
    /**
     * beat消息时为client_licence，代理消息时为代理内容使用的协议 {@link com.devsss.onlyone.core.protocol.ProtocolType}
     */
    private String licence;
    private MsgType msgType;
    private String msgId;
    private int length;

    public enum MsgType {BEAT, REQUEST, RESPONSE}

    /**
     * 含换行符 \n 的消息头
     * @return 消息头
     */
    public String toHeaderMsg() {
        return id + ":" + licence + ":" + msgType.name() + ":" + msgId + ":" + length + "\n";
    }

    public static Header stringToHeader(String header) {
        String[] h = header.split(":");
        return new HeaderBuilder().id(h[0]).licence(h[1]).msgType(stringToEnum(h[2])).msgId(h[3])
                .length(Integer.parseInt(h[4])).build();
    }

    public static MsgType stringToEnum(String type) {
        return switch (type) {
            case "BEAT" -> MsgType.BEAT;
            case "REQUEST" -> MsgType.REQUEST;
            case "RESPONSE" -> MsgType.RESPONSE;
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    /**
     * 生成随机消息id
     * @return  日时分秒 + 3位随机数
     */
    public static String generateMsgId() {
        SimpleDateFormat sdf= new SimpleDateFormat("ddHHmmss");
        int num = (int) (Math.random() * 1000);
        return sdf.format(System.currentTimeMillis()) + num;
    }

}

package com.devsss.onlyone.core.protocol.ssl;

import lombok.Getter;

@Getter
public enum HandshakeType {

    NULL(-1),
    HELLO_REQUEST(0),
    CLIENT_HELLO(1),
    SERVER_HELLO(2),
    CERTIFICATE(11),
    SERVER_KEY_EXCHANGE (12),
    CERTIFICATE_REQUEST(13),
    SERVER_HELLO_DONE(14),
    CERTIFICATE_VERIFY(15),
    CLIENT_KEY_EXCHANGE(16),
    FINISHED(20);

    private final int value;

    HandshakeType(int value) {
        this.value = value;
    }

    public static HandshakeType getByValue(int value) {
        for(HandshakeType handshakeType: HandshakeType.values()) {
            if(handshakeType.getValue() == value) {
                return handshakeType;
            }
        }
        return HandshakeType.NULL;
    }
}

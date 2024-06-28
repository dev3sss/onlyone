package com.devsss.onlyone.core.protocol.ssl;

import lombok.Getter;

@Getter
public enum ContentType {
    CHANGE_CIPHER_SPEC(20),ALERT(21),HANDSHAKE(22),APPLICATION_DATA(23);

    private final int value;

    ContentType(int value) {
        this.value = value;
    }

}

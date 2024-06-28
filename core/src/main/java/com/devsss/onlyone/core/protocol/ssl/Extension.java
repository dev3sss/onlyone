package com.devsss.onlyone.core.protocol.ssl;

import com.devsss.onlyone.core.util.ByteUtils;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.ByteArrayInputStream;
import java.io.IOException;

@Data
@AllArgsConstructor
public class Extension {

    String type; // 2位
    int length; // 2位
    byte[] content;

    public String getHostName() throws IOException {
        if ("0".equals(type)) {
            ByteArrayInputStream b = new ByteArrayInputStream(content);
            // 2位长度+ ServerName
            int listLen = ByteUtils.getInt16(b.readNBytes(2));
            for (int i = 0; i < listLen; i++) {
                // 1位type
                int type = ByteUtils.getInt8(b.readNBytes(1));
                // 2 位长度
                int len = ByteUtils.getInt16(b.readNBytes(2));
                // 内容
                String hostName = new String(b.readNBytes(len));
                if ( 0 == type) {
                    return hostName;
                }
            }
        }
        return null;
    }
}

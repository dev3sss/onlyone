package com.devsss.onlyone.core.net;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

public interface MsgRepeater {

    void acceptResponse(byte[] response) throws IOException;

    void acceptResponse(InputStream response) throws IOException;

    void sendResponse(byte[] response) throws IOException;

    /**
     * 每次执行完成后调用
     * @param completed 对于批量任务，仅最后一次完成时，入参为true
     */
    void onCompleted(Consumer<Boolean> completed);
}

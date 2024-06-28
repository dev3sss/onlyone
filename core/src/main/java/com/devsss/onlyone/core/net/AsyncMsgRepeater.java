package com.devsss.onlyone.core.net;

import com.devsss.onlyone.core.task.AsyncTaskQueue;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

@Slf4j
@AllArgsConstructor
public class AsyncMsgRepeater implements MsgRepeater {

    MsgRepeater repeater;

    AsyncTaskQueue taskQueue;

    @Override
    public void acceptResponse(byte[] response) throws IOException {
        taskQueue.addTask(() -> {
            try {
                repeater.acceptResponse(response);
                return true;
            } catch (IOException e) {
                log.error("消息转发出现错误: {}", e.getMessage());
                return false;
            }
        });
    }

    @Override
    public void acceptResponse(InputStream response) throws IOException {
        taskQueue.addTask(() -> {
            try {
                repeater.acceptResponse(response);
                return true;
            } catch (IOException e) {
                log.error("消息转发出现错误: {}", e.getMessage());
                return false;
            }
        });
    }

    @Override
    public void sendResponse(byte[] response) throws IOException {
        repeater.sendResponse(response);
    }

    @Override
    public void onCompleted(Consumer<Boolean> completed) {
        this.repeater.onCompleted((ok) -> {
            completed.accept(ok);
            if (ok) {
                taskQueue.completed();
            }
        });
    }
}

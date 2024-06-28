package com.devsss.onlyone.core.task;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@Slf4j
public class AsyncTaskQueue {

    private final ConcurrentLinkedQueue<Supplier<Boolean>> queue = new ConcurrentLinkedQueue<>();
    private final Semaphore semaphore = new Semaphore(0);
    @Setter
    private int retry = 3;

    private volatile boolean complete = false;

    public AsyncTaskQueue() {
        // 启动一个虚拟线程执行任务
        Thread.ofVirtual().start(() -> {
            while (!complete) {
                try {
                    semaphore.acquire();
                    if (complete) {
                        break;
                    }
                    Supplier<Boolean> poll = queue.poll();
                    for (int i = 0; i < retry; i++) {
                        if (poll == null || poll.get()) {
                            break;
                        }
                    }
                } catch (InterruptedException e) {
                    log.warn("AsyncTaskQueue执行任务异常: {}", e.getMessage());
                }
            }
            log.debug("任务执行结束");
        });
    }

    public void addTask(Supplier<Boolean> task) {
        queue.add(task);
        semaphore.release();
    }

    public void completed() {
        complete = true;
        semaphore.release();
    }
}

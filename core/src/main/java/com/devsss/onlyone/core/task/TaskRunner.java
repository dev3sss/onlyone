package com.devsss.onlyone.core.task;

import lombok.Data;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

@Data
public class TaskRunner<T> implements Callable<T> {

    private Supplier<T> task;

    @Override
    public T call() {
        return task.get();
    }
}

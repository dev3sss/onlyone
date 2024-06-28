package com.devsss.onlyone.core.protocol.ssl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.nio.channels.ClosedChannelException;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 消息中介所<p>
 * 原始数据通过 {@link RepeaterByteChannel#fromData(byte[])}进入fromBuf,
 * fromBuf每次只允许写入一次数据，在读取之后才能再次写入，中间商通过 {@link RepeaterByteChannel#read(ByteBuffer)} 读取。
 * 中间商将处理后的结果数据通过{@link RepeaterByteChannel#write(ByteBuffer)}写入toBuf,
 * 消费者通过 {@link RepeaterByteChannel#toData()} 一次性读取所有toBuf中的数据。
 * <p>
 * 一个场景是：接收到客户端https请求后，通过fromBuf将数据传递给SSLEngine，SSLEngine完成握手、包装、解包等操作，将待处理消息写入toBuf供
 * 后续处理，比如发送握手消息和包装消息到客户端、解包后的消息发送给代理。
 */
public class RepeaterByteChannel implements ByteChannel {
    private final Lock fromLock = new ReentrantLock();
    private final Lock toLock = new ReentrantLock();
    // 请求数据进一出一
    private final Semaphore fromWriteSemaphore = new Semaphore(1);
    private final Semaphore fromReadSemaphore = new Semaphore(0);
//    private final Semaphore fromSemaphore = new Semaphore(0);
    private final Semaphore toSemaphore = new Semaphore(0);
    private byte[] fromBuf;
    private byte[] toBuf;

    /*
     * The index that is one greater than the last valid byte in the channel.
     */
    private int fromLast;
    private int toLast;

    private boolean closed;

    public RepeaterByteChannel(int sz, boolean readonly) {
        this.fromBuf = new byte[sz];
        this.toBuf = new byte[sz];
        this.fromLast = this.toLast = 0;
    }

    @Override
    public boolean isOpen() {
        return !closed;
    }

    /**
     * 中间商读取fromBuf中的数据，以进行后续处理
     * @param dst 读取的字节
     * @return 读取的长度
     * @throws IOException 异常
     */
    @Override
    public int read(ByteBuffer dst) throws IOException {
        try {
            // 请求读取操作
            fromReadSemaphore.acquire();
            beginRead();
            ensureOpen();
            if (fromLast == 0) {
                return -1;
            }
            int n = Math.min(dst.remaining(), fromLast);
            dst.put(fromBuf, 0, n);
            fromBuf = Arrays.copyOfRange(fromBuf, n, fromLast);
            fromLast -= n;
            // 读完之后允许写入
            fromWriteSemaphore.release();
            return n;
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            endRead();
        }
    }

    /**
     * 中间商将处理后的结果数据放到toBuf，供消费者使用
     * @param src 结果数据
     * @return 内容长度
     * @throws IOException 异常
     */
    @Override
    public int write(ByteBuffer src) throws IOException {
        beginWrite();
        try {
            ensureOpen();
            int n = src.remaining();
            ensureCapacity(toLast + n, false);
            src.get(toBuf, toLast, n);
            toLast += n;
            toSemaphore.release();
            return n;
        } finally {
            endWrite();
        }
    }

    /**
     * 将数据放到fromBuf，供中间商读取
     * @param src 给中间商的数据
     */
    public void fromData(byte[] src) {
        try {
            // 请求写入操作
            fromWriteSemaphore.acquire();
            beginRead();
            ensureOpen();
            int n = src.length;
            ensureCapacity(fromLast + n, true);
            System.arraycopy(src, 0, fromBuf, fromLast, n);
            fromLast += n;
            // 写完之后允许读取
            fromReadSemaphore.release();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            endRead();
        }
    }

    /**
     * 获取中间商处理之后的结果数据
     * @return 结果数据
     */
    public byte[] toData() {
        try {
            toSemaphore.acquire();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        beginWrite();
        try {
            ensureOpen();
            byte[] ret = Arrays.copyOfRange(toBuf, 0, toLast);
            toLast = 0;
            // 由于是一次读取所有，清空信号量
            toSemaphore.drainPermits();
            return ret;
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            endWrite();
        }
    }

    @Override
    public void close() throws IOException {
        if (closed)
            return;
        beginWrite();
        beginRead();
        try {
            closed = true;
            fromBuf = toBuf = null;
            fromLast = toLast = 0;
        } finally {
            endRead();
            endWrite();
        }
    }

    private void ensureOpen() throws IOException {
        if (closed)
            throw new ClosedChannelException();
    }

    /**
     * 争对to的锁
     */
    final void beginWrite() {
        toLock.lock();
    }

    final void endWrite() {
        toLock.unlock();
    }

    /**
     * 争对from的锁
     */
    private void beginRead() {
        fromLock.lock();
    }

    private void endRead() {
        fromLock.unlock();
    }

    private void ensureCapacity(int minCapacity, boolean forFrom) {
        // overflow-conscious code
        if (forFrom) {
            if (minCapacity - fromBuf.length > 0) {
                grow(minCapacity, true);
            }
        } else {
            if (minCapacity - toBuf.length > 0) {
                grow(minCapacity, false);
            }
        }
    }

    /**
     * The maximum size of array to allocate.
     * Some VMs reserve some header words in an array.
     * Attempts to allocate larger arrays may result in
     * OutOfMemoryError: Requested array size exceeds VM limit
     */
    private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * Increases the capacity to ensure that it can hold at least the
     * number of elements specified by the minimum capacity argument.
     *
     * @param minCapacity the desired minimum capacity
     */
    private void grow(int minCapacity, boolean forFrom) {
        // overflow-conscious code
        int oldCapacity = forFrom ? fromBuf.length : toBuf.length;
        int newCapacity = oldCapacity << 1;
        if (newCapacity - minCapacity < 0)
            newCapacity = minCapacity;
        if (newCapacity - MAX_ARRAY_SIZE > 0)
            newCapacity = hugeCapacity(minCapacity);
        if (forFrom) {
            fromBuf = Arrays.copyOf(fromBuf, newCapacity);
        } else {
            toBuf = Arrays.copyOf(toBuf, newCapacity);
        }
    }

    private static int hugeCapacity(int minCapacity) {
        if (minCapacity < 0) // overflow
            throw new OutOfMemoryError("Required length exceeds implementation limit");
        return (minCapacity > MAX_ARRAY_SIZE) ?
                Integer.MAX_VALUE :
                MAX_ARRAY_SIZE;
    }
}

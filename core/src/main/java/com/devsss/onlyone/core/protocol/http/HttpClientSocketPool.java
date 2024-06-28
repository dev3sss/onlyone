package com.devsss.onlyone.core.protocol.http;

import com.devsss.onlyone.core.net.SimpleConnect;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;

import java.io.IOException;
import java.net.Socket;
import java.time.Duration;

@Slf4j
public class HttpClientSocketPool {

    /**
     * 对象池每个key最大实例化对象数
     */
    private static final int TOTAL_PERKEY = 10;
    /**
     * 对象池每个key最大的闲置对象数
     */
    public static final int IDLE_PERKEY = 3;
    private static final long MAX_WAIT_MILLS = 3000L;
    private static final long SOFT_MIN_EVICTABLE_IDLE_MILLS = 60 * 1000L;
    private GenericKeyedObjectPool<String, SimpleConnect> pool;

    public HttpClientSocketPool() {
        GenericKeyedObjectPoolConfig<SimpleConnect> config = new GenericKeyedObjectPoolConfig<>();
        config.setMaxTotal(TOTAL_PERKEY);
        config.setMaxIdlePerKey(IDLE_PERKEY);
        config.setMaxWait(Duration.ofMillis(MAX_WAIT_MILLS));
        config.setSoftMinEvictableIdleTime(Duration.ofMillis(SOFT_MIN_EVICTABLE_IDLE_MILLS));
        config.setJmxEnabled(true);
        config.setJmxNamePrefix("HttpClientSocketPool");
        config.setTestOnBorrow(false);
        config.setTestOnReturn(true);
        config.setTestWhileIdle(false);
        pool = new GenericKeyedObjectPool<>(new SimpleConnectPooledObjectFactory(), config);
    }

    public HttpClientSocketPool(GenericKeyedObjectPoolConfig<SimpleConnect> config) {
        pool = new GenericKeyedObjectPool<>(new SimpleConnectPooledObjectFactory(), config);
    }

    public SimpleConnect getConnect(String key) throws Exception {
        log.debug("getConnect: {}", key);
        return pool.borrowObject(key);
    }

    public void returnConnect(String key, SimpleConnect connect) {
        log.debug("returnConnect: {}", key);
        pool.returnObject(key, connect);
    }

    public void close() {
        pool.close();
        pool = null;
    }

    private static class SimpleConnectPooledObjectFactory extends BaseKeyedPooledObjectFactory<String, SimpleConnect> {
        @Override
        public SimpleConnect create(String s) throws Exception {
            String[] split = s.split(":");
            String hostName = split[0];
            int port = Integer.parseInt(split[1]);
            Socket socket = new Socket(hostName, port);
            return new SimpleConnect(socket, null);
        }

        @Override
        public PooledObject<SimpleConnect> wrap(SimpleConnect connect) {
            return new DefaultPooledObject<>(connect);
        }

        @Override
        public boolean validateObject(String key, PooledObject<SimpleConnect> p) {
            SimpleConnect connect = p.getObject();
            log.debug("开始检查连接:{}", connect);
            try {
                // 请求一个不存在的地址
                connect.write(HttpConstants.ValidateMsg);
                // 读取响应
                HttpMsgRepeatHandler repeatHandler = new HttpMsgRepeatHandler();
                repeatHandler.acceptResponse(connect.getInputStream());
                log.debug("连接可用:{}", connect);
            } catch (IOException e) {
                log.debug("validateObject失败:{}", connect);
                return false;
            }
            return true;
        }

        @Override
        public void destroyObject(String key, PooledObject<SimpleConnect> p) throws Exception {
            p.getObject().stop();
        }
    }
}

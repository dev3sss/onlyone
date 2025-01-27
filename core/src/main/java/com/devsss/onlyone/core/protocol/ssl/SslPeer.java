package com.devsss.onlyone.core.protocol.ssl;

import lombok.extern.slf4j.Slf4j;

import javax.net.ssl.*;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A class that represents an SSL/TLS peer, and can be extended to create a client or a server.
 * <p/>
 * It makes use of the JSSE framework, and specifically the {@link SSLEngine} logic, which
 * is described by Oracle as "an advanced API, not appropriate for casual use", since
 * it requires the user to implement much of the communication establishment procedure himself.
 * More information about it can be found here: http://docs.oracle.com/javase/8/docs/technotes/guides/security/jsse/JSSERefGuide.html#SSLEngine
 * <p/>
 * {@link SslPeer} implements the handshake protocol, required to establish a connection between two peers,
 * which is common for both client and server and provides the abstract {@link SslPeer#read(ByteChannel, SSLEngine)} and
 * {@link SslPeer#write(ByteChannel, SSLEngine, byte[])} methods, that need to be implemented by the specific SSL/TLS peer
 * that is going to extend this class.
 *
 * @author <a href="mailto:alex.a.karnezis@gmail.com">Alex Karnezis</a>
 */
@Slf4j
public abstract class SslPeer {

    /**
     * Will contain this peer's application data in plaintext, that will be later encrypted
     * using {@link SSLEngine#wrap(ByteBuffer, ByteBuffer)} and sent to the other peer. This buffer can typically
     * be of any size, as long as it is large enough to contain this peer's outgoing messages.
     * If this peer tries to send a message bigger than buffer's capacity a {@link BufferOverflowException}
     * will be thrown.
     */
    protected ByteBuffer myAppData;

    /**
     * Will contain this peer's encrypted data, that will be generated after {@link SSLEngine#wrap(ByteBuffer, ByteBuffer)}
     * is applied on {@link SslPeer#myAppData}. It should be initialized using {@link SSLSession#getPacketBufferSize()},
     * which returns the size up to which, SSL/TLS packets will be generated from the engine under a session.
     * All SSLEngine network buffers should be sized at least this large to avoid insufficient space problems when performing wrap and unwrap calls.
     */
    protected ByteBuffer myNetData;

    /**
     * Will contain the other peer's (decrypted) application data. It must be large enough to hold the application data
     * from any peer. Can be initialized with {@link SSLSession#getApplicationBufferSize()} for an estimation
     * of the other peer's application data and should be enlarged if this size is not enough.
     */
    protected ByteBuffer peerAppData;

    /**
     * Will contain the other peer's encrypted data. The SSL/TLS protocols specify that implementations should produce packets containing at most 16 KB of plaintext,
     * so a buffer sized to this value should normally cause no capacity problems. However, some implementations violate the specification and generate large records up to 32 KB.
     * If the {@link SSLEngine#unwrap(ByteBuffer, ByteBuffer)} detects large inbound packets, the buffer sizes returned by SSLSession will be updated dynamically, so the this peer
     * should check for overflow conditions and enlarge the buffer using the session's (updated) buffer size.
     */
    protected ByteBuffer peerNetData;

    /**
     * Will be used to execute tasks that may emerge during handshake in parallel with the server's main thread.
     */
    protected ExecutorService executor = Executors.newSingleThreadExecutor();

    protected abstract void read(ByteChannel byteChannel, SSLEngine engine) throws Exception;

    protected abstract void write(ByteChannel byteChannel, SSLEngine engine, byte[] message) throws Exception;

    protected abstract void watchUnwrap(byte[] data) throws IOException;

    /**
     * Implements the handshake protocol between two peers, required for the establishment of the SSL/TLS connection.
     * During the handshake, encryption configuration information - such as the list of available cipher suites - will be exchanged
     * and if the handshake is successful will lead to an established SSL/TLS session.
     * <p>
     * <p/>
     * A typical handshake will usually contain the following steps:
     *
     * <ul>
     *   <li>1. wrap:     ClientHello</li>
     *   <li>2. unwrap:   ServerHello/Cert/ServerHelloDone</li>
     *   <li>3. wrap:     ClientKeyExchange</li>
     *   <li>4. wrap:     ChangeCipherSpec</li>
     *   <li>5. wrap:     Finished</li>
     *   <li>6. unwrap:   ChangeCipherSpec</li>
     *   <li>7. unwrap:   Finished</li>
     * </ul>
     * <p/>
     * Handshake is also used during the end of the session, in order to properly close the connection between the two peers.
     * A proper connection close will typically include the one peer sending a CLOSE message to another, and then wait for
     * the other's CLOSE message to close the transport link. The other peer from his perspective would read a CLOSE message
     * from his peer and then enter the handshake procedure to send his own CLOSE message as well.
     *
     * @param byteChannel - the socket channel that connects the two peers.
     * @param engine      - the engine that will be used for encryption/decryption of the data exchanged with the other peer.
     * @return True if the connection handshake was successful or false if an error occurred.
     * @throws IOException - if an error occurs during read/write to the socket channel.
     */
    protected boolean doHandshake(ByteChannel byteChannel, SSLEngine engine) throws IOException {

        log.debug("About to do handshake...");

        SSLEngineResult result;
        HandshakeStatus handshakeStatus;

        // NioSslPeer's fields myAppData and peerAppData are supposed to be large enough to hold all message data the peer
        // will send and expects to receive from the other peer respectively. Since the messages to be exchanged will usually be less
        // than 16KB long the capacity of these fields should also be smaller. Here we initialize these two local buffers
        // to be used for the handshake, while keeping client's buffers at the same size.
        int appBufferSize = engine.getSession().getApplicationBufferSize();
        ByteBuffer myAppData = ByteBuffer.allocate(appBufferSize);
        ByteBuffer peerAppData = ByteBuffer.allocate(appBufferSize);
        myNetData.clear();
        peerNetData.clear();
        handshakeStatus = engine.getHandshakeStatus();
        while (true) {
            switch (handshakeStatus) {
                case NEED_UNWRAP:
                    if (byteChannel.read(peerNetData) < 0) {
                        if (engine.isInboundDone() && engine.isOutboundDone()) {
                            return false;
                        }
                        try {
                            engine.closeInbound();
                        } catch (SSLException e) {
                            log.error("This engine was forced to close inbound, without having received the proper SSL/TLS close notification message from the peer, due to end of stream.");
                        }
                        engine.closeOutbound();
                        // After closeOutbound the engine will be set to WRAP state, in order to try to send a close message to the client.
                        handshakeStatus = engine.getHandshakeStatus();
                        break;
                    }
                    peerNetData.flip();
                    try {
                        result = engine.unwrap(peerNetData, peerAppData);
                        peerNetData.compact();
                        handshakeStatus = result.getHandshakeStatus();
                    } catch (SSLException sslException) {
                        log.error("A problem was encountered while processing the data that caused the SSLEngine to abort. Will try to properly close connection...");
                        log.error(sslException.getMessage());
                        engine.closeOutbound();
                        handshakeStatus = engine.getHandshakeStatus();
                        break;
                    }
                    switch (result.getStatus()) {
                        case OK:
//                            if (peerAppData.hasArray()) {
//                                peerAppData.flip();
//                                byte[] unwrapData = Arrays.copyOf(peerAppData.array(), peerAppData.limit());
//                                watchUnwrap(unwrapData);
//                            }
                            break;
                        case BUFFER_OVERFLOW:
                            // Will occur when peerAppData's capacity is smaller than the data derived from peerNetData's unwrap.
                            peerAppData = enlargeApplicationBuffer(engine, peerAppData);
                            break;
                        case BUFFER_UNDERFLOW:
                            // Will occur either when no data was read from the peer or when the peerNetData buffer was too small to hold all peer's data.
                            peerNetData = handleBufferUnderflow(engine, peerNetData);
                            break;
                        case CLOSED:
                            if (engine.isOutboundDone()) {
                                return false;
                            } else {
                                engine.closeOutbound();
                                handshakeStatus = engine.getHandshakeStatus();
                                break;
                            }
                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                    break;
                case NEED_WRAP:
                    myNetData.clear();
                    try {
                        result = engine.wrap(myAppData, myNetData);
                        handshakeStatus = result.getHandshakeStatus();
                    } catch (SSLException sslException) {
                        log.error("A problem was encountered while processing the data that caused the SSLEngine to abort. Will try to properly close connection...");
                        engine.closeOutbound();
                        handshakeStatus = engine.getHandshakeStatus();
                        break;
                    }
                    switch (result.getStatus()) {
                        case OK:
                            myNetData.flip();
                            while (myNetData.hasRemaining()) {
                                byteChannel.write(myNetData);
                            }
                            break;
                        case BUFFER_OVERFLOW:
                            // Will occur if there is not enough space in myNetData buffer to write all the data that would be generated by the method wrap.
                            // Since myNetData is set to session's packet size we should not get to this point because SSLEngine is supposed
                            // to produce messages smaller or equal to that, but a general handling would be the following:
                            myNetData = enlargePacketBuffer(engine, myNetData);
                            break;
                        case BUFFER_UNDERFLOW:
                            throw new SSLException("Buffer underflow occured after a wrap. I don't think we should ever get here.");
                        case CLOSED:
                            try {
                                myNetData.flip();
                                while (myNetData.hasRemaining()) {
                                    byteChannel.write(myNetData);
                                }
                                // At this point the handshake status will probably be NEED_UNWRAP so we make sure that peerNetData is clear to read.
                                peerNetData.clear();
                            } catch (Exception e) {
                                log.error("Failed to send server's CLOSE message due to socket channel's failure.");
                                log.error(e.getMessage());
                                handshakeStatus = engine.getHandshakeStatus();
                            }
                            break;
                        default:
                            throw new IllegalStateException("Invalid SSL status: " + result.getStatus());
                    }
                    break;
                case NEED_TASK:
                    Runnable task;
                    while ((task = engine.getDelegatedTask()) != null) {
                        executor.execute(task);
                    }
                    handshakeStatus = engine.getHandshakeStatus();
                    break;
                case FINISHED:
                    log.debug("Handshake finished successfully.");
                    return true;
                case NOT_HANDSHAKING:
                    log.debug("Handshake not in progress.");
                    return false;
                default:
                    throw new IllegalStateException("Invalid SSL status: " + handshakeStatus);
            }
        }
    }

    protected ByteBuffer enlargePacketBuffer(SSLEngine engine, ByteBuffer buffer) {
        return enlargeBuffer(buffer, engine.getSession().getPacketBufferSize());
    }

    protected ByteBuffer enlargeApplicationBuffer(SSLEngine engine, ByteBuffer buffer) {
        return enlargeBuffer(buffer, engine.getSession().getApplicationBufferSize());
    }

    /**
     * Compares <code>sessionProposedCapacity<code> with buffer's capacity. If buffer's capacity is smaller,
     * returns a buffer with the proposed capacity. If it's equal or larger, returns a buffer
     * with capacity twice the size of the initial one.
     *
     * @param buffer                  - the buffer to be enlarged.
     * @param sessionProposedCapacity - the minimum size of the new buffer, proposed by {@link SSLSession}.
     * @return A new buffer with a larger capacity.
     */
    protected ByteBuffer enlargeBuffer(ByteBuffer buffer, int sessionProposedCapacity) {
        if (sessionProposedCapacity > buffer.capacity()) {
            buffer = ByteBuffer.allocate(sessionProposedCapacity);
        } else {
            buffer = ByteBuffer.allocate(buffer.capacity() * 2);
        }
        return buffer;
    }

    /**
     * Handles {@link SSLEngineResult.Status#BUFFER_UNDERFLOW}. Will check if the buffer is already filled, and if there is no space problem
     * will return the same buffer, so the client tries to read again. If the buffer is already filled will try to enlarge the buffer either to
     * session's proposed size or to a larger capacity. A buffer underflow can happen only after an unwrap, so the buffer will always be a
     * peerNetData buffer.
     *
     * @param buffer - will always be peerNetData buffer.
     * @param engine - the engine used for encryption/decryption of the data exchanged between the two peers.
     * @return The same buffer if there is no space problem or a new buffer with the same data but more space.
     * @throws Exception
     */
    protected ByteBuffer handleBufferUnderflow(SSLEngine engine, ByteBuffer buffer) {
        if (engine.getSession().getPacketBufferSize() < buffer.limit()) {
            return buffer;
        } else {
            ByteBuffer replaceBuffer = enlargePacketBuffer(engine, buffer);
            buffer.flip();
            replaceBuffer.put(buffer);
            return replaceBuffer;
        }
    }

    /**
     * This method should be called when this peer wants to explicitly close the connection
     * or when a close message has arrived from the other peer, in order to provide an orderly shutdown.
     * <p/>
     * It first calls {@link SSLEngine#closeOutbound()} which prepares this peer to send its own close message and
     * sets {@link SSLEngine} to the <code>NEED_WRAP</code> state. Then, it delegates the exchange of close messages
     * to the handshake method and finally, it closes socket channel.
     *
     * @param byteChannel - the transport link used between the two peers.
     * @param engine      - the engine used for encryption/decryption of the data exchanged between the two peers.
     * @throws IOException if an I/O error occurs to the socket channel.
     */
    protected void closeConnection(ByteChannel byteChannel, SSLEngine engine) throws IOException {
        engine.closeOutbound();
        doHandshake(byteChannel, engine);
        byteChannel.close();
    }

    /**
     * In addition to orderly shutdowns, an unorderly shutdown may occur, when the transport link (socket channel)
     * is severed before close messages are exchanged. This may happen by getting an -1 or {@link IOException}
     * when trying to read from the socket channel, or an {@link IOException} when trying to write to it.
     * In both cases {@link SSLEngine#closeInbound()} should be called and then try to follow the standard procedure.
     *
     * @param byteChannel - the transport link used between the two peers.
     * @param engine      - the engine used for encryption/decryption of the data exchanged between the two peers.
     * @throws IOException if an I/O error occurs to the socket channel.
     */
    protected void handleEndOfStream(ByteChannel byteChannel, SSLEngine engine) throws IOException {
        try {
            engine.closeInbound();
        } catch (Exception e) {
            log.error("This engine was forced to close inbound, without having received the proper SSL/TLS close notification message from the peer, due to end of stream.");
        }
        closeConnection(byteChannel, engine);
    }

    /**
     * 根据keyStore和aliasList创建新的keyStore, 然后创建key managers, 用于初始化 {@link SSLContext}
     *
     * @param keyStore    - 原始keyStore
     * @param keyPassword - the key's passsword.
     * @param aliasList   - 根据该list过滤keyStore
     * @return {@link KeyManager} array that will be used to initiate the {@link SSLContext}.
     * @throws Exception 异常
     */
    protected static KeyManager[] createKeyManagers(KeyStore keyStore, String keyPassword, List<String> aliasList) throws Exception {
        KeyStore store = KeyStore.getInstance("JKS");
        store.load(null);
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        if (aliasList != null && !aliasList.isEmpty()) {
            aliasList.forEach(alias -> {
                try {
                    if (keyStore.containsAlias(alias)) {
                        store.setKeyEntry(alias, keyStore.getKey(alias, keyPassword.toCharArray()), keyPassword.toCharArray(),
                                keyStore.getCertificateChain(alias));
                    }
                } catch (KeyStoreException | UnrecoverableKeyException | NoSuchAlgorithmException e) {
                    throw new RuntimeException(e);
                }
            });
            kmf.init(store, keyPassword.toCharArray());
        } else {
            kmf.init(keyStore, keyPassword.toCharArray());
        }
        return kmf.getKeyManagers();
    }

    /**
     * Creates the trust managers required to initiate the {@link SSLContext}, using a JKS keystore as an input.
     *
     * @param filepath         - the path to the JKS keystore.
     * @param keystorePassword - the keystore's password.
     * @return {@link TrustManager} array, that will be used to initiate the {@link SSLContext}.
     * @throws Exception
     */
    protected static TrustManager[] createTrustManagers(String filepath, String keystorePassword) throws Exception {
        KeyStore trustStore = KeyStore.getInstance("JKS");
        InputStream trustStoreIS = new FileInputStream(filepath);
        try {
            trustStore.load(trustStoreIS, keystorePassword.toCharArray());
        } finally {
            if (trustStoreIS != null) {
                trustStoreIS.close();
            }
        }
        TrustManagerFactory trustFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustFactory.init(trustStore);
        return trustFactory.getTrustManagers();
    }

}

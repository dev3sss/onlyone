package com.devsss.onlyone.server.runner;

import com.devsss.onlyone.core.net.Server;
import com.devsss.onlyone.core.protocol.internal.InternalProtocolConstants;
import com.devsss.onlyone.core.protocol.ssl.SslServer;
import com.devsss.onlyone.core.util.KeyStoreUtils;
import com.devsss.onlyone.server.config.ServerConfig;
import com.devsss.onlyone.server.entity.ClientEntity;
import com.devsss.onlyone.server.repository.ClientRepository;
import com.devsss.onlyone.server.util.GyUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Getter
@Slf4j
@Component
public class OnlyOneServer {

    ServerConfig config;

    Server server;

    ClientRepository clientRepository;

    public OnlyOneServer(ServerConfig config, ClientRepository clientRepository) {
        this.config = config;
        this.clientRepository = clientRepository;
    }

    public void run() throws Exception {
        server = new Server(config.getPort());
        SslServer.trustFile = Objects.requireNonNull(this.getClass().getClassLoader().getResource("trustedCerts.jks")).getPath();
        SslServer.kmsFile = config.getJks();
        SslServer.keyStore = KeyStoreUtils.loadKeyStore(SslServer.kmsFile, SslServer.keyStorePassword);
        server.setMsgKey((id, licence) -> {
            Optional<ClientEntity> entityOptional = clientRepository.findById(id);
            if (entityOptional.isEmpty() || !licence.equals(entityOptional.get().getLicense())) {
                throw new RuntimeException("id与licence不匹配");
            }
            ClientEntity clientEntity = entityOptional.get();
            return GyUtils.isNull(clientEntity.getMsgKey()) ? InternalProtocolConstants.INTERNAL_PROTOCOL_DEFAULT_MSG_KEY
                    : clientEntity.getMsgKey();
        });
        log.info("代理开始启动,端口：{}  管理界面端口: {}", config.getPort(), config.getServerManagerPort());
        server.listen();
    }
}

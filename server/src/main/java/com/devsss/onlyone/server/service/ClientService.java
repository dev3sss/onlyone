package com.devsss.onlyone.server.service;

import com.devsss.onlyone.server.entity.ClientEntity;
import com.devsss.onlyone.server.repository.ClientRepository;
import com.devsss.onlyone.server.runner.OnlyOneServer;
import com.devsss.onlyone.server.util.GyUtils;
import com.devsss.onlyone.server.vo.ClientEntityEx;
import com.devsss.onlyone.server.vo.PageInfo;
import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@AllArgsConstructor
@Service
public class ClientService {

    ClientRepository clientRepository;

    OnlyOneServer oneServer;

    public ClientEntity findById(String id) {
        Optional<ClientEntity> clientEntity = clientRepository.findById(id);
        return clientEntity.orElseGet(() -> new ClientEntity(id, "", "", "", false, null, null));
    }

    public ClientEntity save(ClientEntity client) {
        if (GyUtils.isNull(client.getId())) {
            client.setId(RandomStringUtils.randomAlphabetic(8));
        }
        if (GyUtils.isNull(client.getLicense())) {
            client.setLicense(RandomStringUtils.randomAlphabetic(16));
        }
        if (GyUtils.isNull(client.getMsgKey())) {
            client.setMsgKey(RandomStringUtils.randomAlphabetic(16));
        }
        return clientRepository.save(client);
    }

    public PageInfo<ClientEntityEx> findAll(int pageNumber, int pageSize, String id, String bz) {
        // 页码是从0开始,实际场景中是从1开始
        Pageable pageable = PageRequest.of(pageNumber - 1, pageSize);
        Page<ClientEntity> all = clientRepository.findAllByIdContainsAndBzContainsOrderByLrrqDesc(id, bz, pageable);
        PageInfo<ClientEntityEx> pageInfo = new PageInfo<>();
        pageInfo.setPageSize(pageSize);
        pageInfo.setCurrent(pageNumber);
        pageInfo.setTotal((int) all.getTotalElements());
        List<ClientEntityEx> cexList = new ArrayList<>();
        Map<String, List<String>> clientProxyList = oneServer.getServer().getClientProxyList();
        Gson gson = new Gson();
        // 获取连接状态
        all.getContent().forEach( c -> {
            String cliStr = gson.toJson(c);
            ClientEntityEx ex = gson.fromJson(cliStr,ClientEntityEx.class);
            ex.setProxy(clientProxyList.get(c.getId()));
            cexList.add(ex);
        });
        pageInfo.setContent(cexList);
        return pageInfo;
    }

    public int updateYxbzById(ClientEntity client) {
        return clientRepository.updateYxbzById(client.getId(), client.isYxbz());
    }

    public void delById(String id) {
        clientRepository.deleteById(id);
    }
}

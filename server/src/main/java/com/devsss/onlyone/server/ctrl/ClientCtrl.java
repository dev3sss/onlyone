package com.devsss.onlyone.server.ctrl;

import com.devsss.onlyone.server.entity.ClientEntity;
import com.devsss.onlyone.server.service.ClientService;
import com.devsss.onlyone.server.vo.ClientEntityEx;
import com.devsss.onlyone.server.vo.PageInfo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/client")
public class ClientCtrl {

    ClientService clientService;

    @GetMapping("/{id}")
    public ClientEntity findById(@PathVariable String id) {
        return clientService.findById(id);
    }

    @GetMapping("/findAll")
    public PageInfo<ClientEntityEx> findAll(@RequestParam int n, @RequestParam int s,
                                            @RequestParam String id, @RequestParam String bz) {
        return clientService.findAll(n, s, id, bz);
    }

    @PostMapping
    public ClientEntity save(@RequestBody ClientEntity client) {
        return clientService.save(client);
    }

    @PutMapping
    public int updateYxbzById(@RequestBody ClientEntity client) {
        return clientService.updateYxbzById(client);
    }

    @DeleteMapping("/{id}")
    public void delClientById(@PathVariable String id) {
        clientService.delById(id);
    }
}

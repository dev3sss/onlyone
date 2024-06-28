package com.devsss.onlyone.server.ctrl;

import com.devsss.onlyone.server.service.KeyStoreService;
import com.devsss.onlyone.server.vo.PemFileInfo;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/keystore")
public class KeyStoreCtrl {

    KeyStoreService keyStoreService;

    @PostMapping("/uploadPem")
    public PemFileInfo writeFile(@RequestParam("file") MultipartFile file) throws IOException {
        return keyStoreService.savePem(file);
    }

    @PostMapping("/save")
    public void saveZs(@RequestBody Map<String, PemFileInfo> req) throws Exception {
        PemFileInfo key = req.get("key");
        PemFileInfo cert = req.get("cert");
        keyStoreService.saveZs(key,cert);
    }

    @GetMapping("/{alias}")
    public PemFileInfo queryPemInfo(@PathVariable String alias) throws Exception {
        return keyStoreService.getKeyInfo(alias);
    }

    @GetMapping
    public List<PemFileInfo> queryAllPemInfo() throws Exception {
        return keyStoreService.getAllKeyInfo();
    }

}

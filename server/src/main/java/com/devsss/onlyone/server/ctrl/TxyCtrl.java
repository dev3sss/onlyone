package com.devsss.onlyone.server.ctrl;

import com.devsss.onlyone.server.entity.CertInfoEntity;
import com.devsss.onlyone.server.service.TencentCloudService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/txy")
public class TxyCtrl {

    TencentCloudService tencentCloudService;

    @GetMapping("/search")
    public List<CertInfoEntity> search(@RequestParam String domainName) throws Exception {
        return tencentCloudService.describeCertificates(domainName);
    }

    @PostMapping("/addToJks")
    public CertInfoEntity addToJks(@RequestBody CertInfoEntity certInfo) throws Exception {
        return tencentCloudService.addToJks(certInfo);
    }
}

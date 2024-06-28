package com.devsss.onlyone.server.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

@EqualsAndHashCode(callSuper = true)
@Data
public class PemFileInfo extends FileInfo{
    String fileType;
    Date notBefore;
    Date notAfter;
    String issuer;
    String subject;
    String algorithm;
    String alias;
}

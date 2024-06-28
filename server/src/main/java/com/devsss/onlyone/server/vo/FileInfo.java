package com.devsss.onlyone.server.vo;

import lombok.Data;

@Data
public class FileInfo {
    String originalFilename;
    String saveFilename;
    long size;
}

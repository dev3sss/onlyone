package com.devsss.onlyone.server.vo;

import lombok.Data;

import java.util.List;

@Data
public class PageInfo<T> {
    private int total;
    private int current;
    private int pageSize;
    private List<T> content;
}

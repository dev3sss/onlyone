package com.devsss.onlyone.server.vo;

import com.devsss.onlyone.server.entity.ClientEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class ClientEntityEx extends ClientEntity {

    List<String> proxy;
}

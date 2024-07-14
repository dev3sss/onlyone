# onlyone
服务端暴露一个端口接收tcp请求，转发http请求到实际的内网服务。

## server
启动服务端，监听tcp请求，并提供相关管理服务。

### ssl证书申请
采用腾讯云api，需配置secret_id和secret_key，具体参考 [tencentcloud-sdk-java](https://github.com/TencentCloud/tencentcloud-sdk-java) 。
配置文件路径要求为：
```
Windows: c:\Users\NAME\.tencentcloud\credentials
Linux: ~/.tencentcloud/credentials 或 /etc/tencentcloud/credentials
```

配置文件格式如下：
```
[default]
secret_id = xxxxx
secret_key = xxxxx
```

## client
接收server转发过来的http请求，转发到实际的内网服务。需要配置以下信息：
```
onlyone:
  admin:                  # web端帐号配置
    user: admin           # 管理员账号
    password: <PASSWORD>  # 管理员密码
  server:
    host: 127.0.0.1    # 服务端ip,供客户端连接
    port: 9999         # 服务端端口
    jks: server.jks    # 服务端证书存储文件
    filepath: D:\down  # 保存文件路径
  client:
    id: id
    licence: 123456
    msgKey: 123456
    proxy:
      - url: test.abcd.test  # 可处理的url前缀
        ip: 127.0.0.1  # 实际处理程序的ip
        port: 9001     # 实际处理程序的端口
```


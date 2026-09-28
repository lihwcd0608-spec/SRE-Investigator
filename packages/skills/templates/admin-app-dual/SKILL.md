---
name: admin-app-dual
description: 适用于后台管理端和 C 端接口分仓的微服务项目。
tools: [code, log_by_trace, nacos_config_read]
danger: readonly
---

# Admin + App 双端排查模板

先判定请求来自 B 端运营后台还是 C 端 APP/H5，再选择对应仓库、网关和认证服务。

对于 demo 样例：

- Admin：`demo-admin-broad`，包根 `com.example.admin`
- App：`demo-app-broad`，包根 `com.example.app`

两侧 Remote 接口名称可能相同，但不能把契约 JAR 或服务实现混为同一接口。跨端问题须分别列出每侧证据。

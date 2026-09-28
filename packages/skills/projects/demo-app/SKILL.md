---
name: demo-app
description: demo-app 项目覆盖：管理后台和 APP 双仓、Nacos、Dubbo 的排查上下文。
tools: [code, mysql_readonly, redis_readonly, es_search, log_by_trace, nacos_config_read]
danger: readonly
---

# Demo App 覆盖规则

仓库角色：

- `demo-admin-broad`：运营后台；入口 `demo-gateway`，默认 8080。
- `demo-app-broad`：C 端；入口 `demo-app-gateway`，默认 19090。

服务配置均从 Nacos 读取。排查前确认环境 namespace，避免把 test/gray/prod 的证据混用。

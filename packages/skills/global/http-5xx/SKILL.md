---
name: http-5xx
description: 排查 HTTP 5xx、网关错误和服务超时。仅使用当前项目绑定的只读工具。
tools: [code, log_by_trace, mysql_readonly, redis_readonly]
danger: readonly
---

# HTTP 5xx 排查

1. 先确认环境、时间范围、请求路径、traceId 和调用方。
2. 有 SLS 时用 sls_list_logstores 确认 logstore，再 sls_query_readonly 按 traceId 拉网关/应用日志。
3. 有 MSE 时用 mse_list_routes / mse_get_service 核对路由、后端和实例健康，再用 mse_list_nacos_instances 看注册实例。
4. 查服务日志与调用链，再检查 Dubbo 或 HTTP 下游。
5. 只在已有证据指向数据层时，使用只读数据库和 Redis 工具。
6. 输出事实、已排除项、最多三个按概率排序的假设；没有证据时明确说明。

不得修改配置、重启服务、写库、删除缓存或建议直接执行高危命令。

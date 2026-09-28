# 阿里云排障工具：全局 RAM 只读 AK

ARMS、ALB/CLB、CDN **共用一套进程级 RAM 子账号**，不要用主账号 AK，也不要在 Binding 里再填 AccessKey。

Binding 只保存：

- ARMS：`regionId` + 可选应用名白名单
- 负载均衡：`regionId` + `ALB`/`CLB` + 可选实例 ID 白名单
- CDN：可选加速域名白名单

## 环境变量

```bash
export ALIBABA_CLOUD_ACCESS_KEY_ID="LTAI..."
export ALIBABA_CLOUD_ACCESS_KEY_SECRET="..."
```

对应 `application.yml` 的 `investigator.aliyun.access-key-id` / `access-key-secret`。未配置时，工具页测试连接会提示缺 AK，Agent 也不会挂这三类工具以外的调用（没有 Binding 同样不会挂）。

## 最小 RAM 策略

只开放 List/Get/Describe/Search。不要授权 Put/Update/Delete/Create。

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "arms:ListTraceApps",
        "arms:SearchTraces",
        "arms:GetTrace"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "alb:ListLoadBalancers",
        "alb:GetListenerHealthStatus"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "slb:DescribeLoadBalancers",
        "slb:DescribeHealthStatus"
      ],
      "Resource": "*"
    },
    {
      "Effect": "Allow",
      "Action": [
        "cdn:DescribeUserDomains",
        "cdn:DescribeCdnDomainDetail",
        "cdn:DescribeDomainUsageData"
      ],
      "Resource": "*"
    }
  ]
}
```

审计只记录 region、应用名、实例 ID、域名、时间窗和 `traceId` 后 8 位，不写 AK、不写完整调用链。

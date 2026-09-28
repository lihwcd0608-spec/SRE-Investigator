---
name: order-payment
description: 排查订单创建、支付回调、幂等和状态不一致问题。
tools: [code, mysql_readonly, redis_readonly, log_by_trace]
danger: readonly
---

# 订单与支付排查

1. 收集 orderId、paymentId、用户、渠道、金额、币种、时间和回调 requestId。
2. 按时间轴核验订单、支付单、回调日志、消息消费结果和幂等记录。
3. 避免假设订单表名；先查 schema 或从当前项目代码确认。
4. 只读核验后给出状态差异和补偿建议，但不执行补单、退款、重放消息。

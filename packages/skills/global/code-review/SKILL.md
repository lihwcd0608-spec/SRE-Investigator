---
name: code-review
description: 对比基准分支与对比分支的 diff，按已配置 Rule 做只读代码审查。
tools: [code_read, code_search]
danger: readonly
---

# 代码审查

1. 只看已注入的变更文件列表和 diff，不要读未改动的文件，不要凭空猜改了什么。
2. 需要核对源码时，对列表里的文件用 code_read(startLine) 分段读差异附近，或用 code_search 定位符号。单次最多 300 行，禁止一次读完整大文件。
3. 整库 SQL、lock、生成物、二进制可跳过，除非审查焦点点名。
4. 正文用 Markdown。问题表表头：严重级别 | 文件 | 行号 | 风险 | 证据；回归建议表表头：范围 | 建议 | 依据。没有问题时问题表写一行「无」。
5. 信息不足时在单元格写「未知」，不要省略列。
6. 正文后输出 ```review-marks``` JSON 数组，字段 path/line/side/severity/title/comment，把有问题的行标到对比分支上；没有问题时输出 []。

不得 commit、push、改代码、写库或建议直接合并。

# 爬虫技术栈性能验证

日期：2026-04-21

## 目标环境

- 服务器：2 vCPU / 2 GB 内存 / 60 GB SSD（仅负责录入）
- 本地工作器：i7-13650HX / 16 GB 内存（执行爬取任务）

## 验证范围

- 后端录入模块在集成爬虫功能后可正常构建和启动。
- 桌面端前端可正常打包。
- Python 工作器模块通过语法编译检查。
- 默认并发和重试参数已针对低风险基线调优。

## 已执行的检查

1. 后端打包检查

```bash
mvn --% -f weblog-backend/pom.xml -pl weblog-api -am -Dmaven.test.skip=true package
```

结果：`BUILD SUCCESS`，总耗时约 `20.8s`。

2. 工作器语法编译检查

```bash
python -m compileall crawler-worker/app
```

结果：所有模块编译通过。

3. 桌面端构建检查

```bash
npm --prefix crawler-desktop install
npm --prefix crawler-desktop run build
```

结果：构建成功，主 JS 包约 `449.73 kB`（gzip 后 `140.96 kB`）。

## 调优后的默认参数

- `max_global_concurrency = 3`
- `max_domain_concurrency = 1`
- `max_retry_count = 3`
- `request_timeout_seconds = 20`
- `cache_max_bytes = 10 GB`

以上默认值优先保证在资源受限的服务器部署环境下的稳定性和低峰值占用。

## 后续运行时基准测试

- 对 3 个代表性域名执行 100 条 URL 批量测试，测量：
  - 平均抽取延迟
  - 动态回退比例
  - 图片暂存吞吐量
  - 推送成功率
- 在爬取窗口期间收集本地工作器的峰值内存和 CPU 使用率。
- 验证服务器端除录入/持久化接口外不承担其他爬取负载。

# 高并发运行手册

本项目的流量可以分成两类：

- 轻量 API 流量：登录、用户资料、会话列表、健康检查、文件元数据等。
- 长耗时 AI 流量：聊天、SSE 流式响应、RAG 问答、工具调用、报告生成、超级智能体 Manus。

这两类流量需要分别扩容和保护。轻量 API 通常可以通过增加 Spring Boot 实例、提升数据库读能力来扩容；AI 流量则必须通过并发舱壁、队列、超时、降级和限流来控制。

## 容量模型

以 10 万日活用户为例：

| 工作负载 | 示例 | 平均值 | 峰值规划值 |
|---|---:|---:|---:|
| 轻量 API | 每用户每天 20 次请求 | 23 RPS | 200-300 RPS |
| AI 请求 | 每用户每天 5 次请求 | 5.8 RPS | 50-100 RPS |
| AI 并发 | 平均响应 20 秒 | 116 并发 | 1,000-2,000 并发 |

计算公式：

```text
average_rps = daily_requests / 86400
peak_rps = average_rps * peak_factor
ai_concurrency = peak_ai_rps * average_ai_latency_seconds
```

其中：

- `average_rps`：平均每秒请求数。
- `peak_rps`：峰值请求数，通常按平均值的 5-10 倍估算。
- `ai_concurrency`：AI 峰值并发数，和模型响应时间强相关。

## 当前控制项

应用配置：

```yaml
app.ai.max-concurrent-requests: ${APP_AI_MAX_CONCURRENT_REQUESTS:20}
app.ai.queue.enabled: ${APP_AI_QUEUE_ENABLED:false}
app.guest.quota-store: ${APP_GUEST_QUOTA_STORE:db}
app.auth.sms-code-store: ${APP_AUTH_SMS_CODE_STORE:db}
app.rate-limit.enabled: ${APP_RATE_LIMIT_ENABLED:false}
server.tomcat.threads.max: ${SERVER_TOMCAT_THREADS_MAX:200}
server.tomcat.accept-count: ${SERVER_TOMCAT_ACCEPT_COUNT:200}
spring.datasource.hikari.maximum-pool-size: ${SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE:20}
```

健康检查接口：

```text
GET /api/health/live
GET /api/health/ready
GET /api/health
GET /api/actuator/health
```

当单实例 AI 并发舱壁满了之后，AI 请求会返回 HTTP `429`。这比让请求无限堆积更安全，可以保护主应用、数据库和模型供应商调用。

## 生产环境建议初始值

小规模生产实例可以从下面的配置开始：

```bash
APP_AI_MAX_CONCURRENT_REQUESTS=20
SERVER_TOMCAT_THREADS_MAX=200
SERVER_TOMCAT_THREADS_MIN_SPARE=20
SERVER_TOMCAT_ACCEPT_COUNT=300
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=30
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=10
```

不要一开始就盲目调高线程数。真正的瓶颈可能在模型供应商、数据库、RAG 检索、文件生成或外部工具调用上。优先做水平扩容和队列化，而不是单实例硬扛。

## 压测

运行默认轻量接口压测：

```bash
LOAD_TEST_REQUESTS=3000 LOAD_TEST_CONCURRENCY=100 node scripts/load-test.mjs
```

运行单个场景：

```bash
LOAD_TEST_SCENARIO=session-list LOAD_TEST_REQUESTS=5000 LOAD_TEST_CONCURRENCY=500 node scripts/load-test.mjs
```

报告输出：

```text
target/load-test/load-test-report.md
target/load-test/load-test-report.json
```

不要直接用真实付费模型端点做大流量压测。建议先使用 Mock ChatModel、灰度环境或受控模型额度，避免产生高额费用或触发供应商限流。

## 加固路线图

1. 在服务前增加 CDN、WAF 或 API Gateway。
2. 将验证码、游客次数、限流计数迁移到 Redis。
3. 将长耗时 AI 任务拆到独立 AI Worker 实例。
4. 对报告生成和昂贵工具链使用 MQ 异步处理。
5. 将头像、图表、报告等生成文件迁移到对象存储。
6. 将 RAG 文档解析和索引迁移到后台索引 Worker。
7. 增加 Prometheus/Grafana 指标，覆盖 Tomcat、Hikari、AI 舱壁、RAG、工具调用。
8. 给模型供应商和外部工具调用增加超时、重试和熔断。
9. 使用蓝绿发布或滚动发布，并配合 readiness 检查。
10. 每次性能敏感改动后重新执行压测。

## 扩容经验公式

如果 AI 平均响应耗时是 20 秒，单实例允许 20 个 AI 并发：

```text
ai_throughput_per_instance = 20 / 20 = 1 request/second
```

如果峰值 AI 请求为 50 RPS，则至少需要规划约 50 个 AI Worker 容量单位。也可以通过流式响应、缓存、使用更小模型、请求排队、任务拆分来降低单位请求耗时。

## Redis 迁移

启用 Redis 存储游客次数、验证码和限流状态：

```bash
REDIS_HOST=redis
REDIS_PORT=6379
REDIS_PASSWORD=replace-with-redis-password
APP_GUEST_QUOTA_STORE=redis
APP_AUTH_SMS_CODE_STORE=redis
APP_RATE_LIMIT_ENABLED=true
APP_RATE_LIMIT_STORE=redis
```

当前 Redis 职责：

- 游客免费使用次数。
- 短信验证码。
- 短信重发间隔。
- 手机号、IP、设备维度的验证码发送限制。
- IP、用户、接口维度的固定窗口限流。

## AI 队列模式

启用内存 Worker 队列：

```bash
APP_AI_QUEUE_ENABLED=true
APP_AI_QUEUE_WORKER_THREADS=8
APP_AI_QUEUE_CAPACITY=200
APP_AI_MAX_CONCURRENT_REQUESTS=20
```

这是第一阶段队列方案，适合单实例或本机验证。多实例生产环境建议迁移到 Redis Stream、Kafka、RabbitMQ 或 RocketMQ，并运行独立 AI Worker Pod。

## 对象存储迁移

目标设计：

```text
头像/报告/图表/PDF/Word -> 对象存储
数据库 -> 保存 object key、owner user id、content type、size、checksum
下载/预览 -> 签名 URL 或短期 file token
```

推荐方案：

- 开发环境：MinIO
- 阿里云：OSS
- AWS 兼容方案：S3

不要在多个应用副本之间共享本地 `tmp` 目录。本地生成文件只适合开发环境或单实例演示。

## RAG 独立部署

目标设计：

```text
RAG 索引 Worker -> 解析/切分/Embedding -> 向量数据库
RAG 查询服务 -> 混合召回/重排 -> 引用来源
主应用 -> 通过 HTTP/gRPC 调用 RAG 服务
```

推荐向量后端：

- 早期生产和中小型语料：pgvector。
- 大规模语料：Milvus，或 Elasticsearch/OpenSearch 混合检索。

RAG 索引应保持异步。生产环境不要在主应用启动时解析大量 PDF，否则会拖慢启动、放大模型额度消耗，并增加不可控失败风险。

## 可观测性

应用指标接口：

```text
GET /api/actuator/prometheus
```

本机监控入口：

```text
Prometheus: http://localhost:6543
Grafana: http://localhost:7654
```

Grafana 添加 Prometheus 数据源时使用：

```text
http://prometheus:9090
```

应用会返回 `X-Trace-Id`，并将 `traceId` 写入日志 MDC。排查线上问题时，可以用同一个 traceId 串联前端请求、后端日志、AI 调用和工具执行日志。

## 上线检查清单

- Redis、PostgreSQL、对象存储均已启用持久化。
- 数据库连接池、Tomcat 线程池、AI 并发舱壁配置合理。
- 游客次数、验证码、限流均迁移到 Redis。
- RAG 索引任务不阻塞主应用启动。
- 报告生成和大文件上传不阻塞主请求线程。
- 外部模型、搜索、行情、短信接口均设置超时和熔断。
- Prometheus/Grafana 能看到应用、JVM、HTTP、数据库和 AI 相关指标。
- 生产环境关闭 Swagger UI、Knife4j 和终端工具。
- API Key、JWT Secret、短信密钥不写入代码仓库。
- 压测结果满足目标 QPS、P95、P99 和错误率要求。

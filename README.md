# AI 金融智能体平台

基于 Spring Boot、Spring AI、Vue 3、Redis、PostgreSQL 和 pgvector 构建的金融智能体应用，支持基础聊天、专业 Agent、RAG 知识库、行情/搜索工具调用、图文报告生成、登录会话隔离、限流队列和 Docker Compose 部署。

## 核心能力

- AI 对话：基础问答、专业 Agent、SSE 流式响应、上下文会话隔离。
- 金融工具：搜索、财经新闻、行情、网页抓取、图表、报告、文件下载。
- RAG 知识库：Markdown/PDF 资料切分、pgvector 向量检索、文件 hash 增量索引。
- 登录体系：游客免费次数、手机号验证码登录、密码登录、头像上传、资料修改。
- 报告生成：Markdown、HTML、PDF、Word 多格式输出，图片和表格按正式报告渲染。
- 工程能力：Redis 限流、AI 队列 Worker、traceId、Actuator、Prometheus、Grafana。
- 安全部署：前后端容器默认仅绑定本机端口，公网通过宿主机 HTTPS 反向代理进入。

## 技术栈

| 模块 | 技术 |
|---|---|
| 前端 | Vue 3, Vite, Pinia, Axios, Nginx |
| 后端 | Java 21, Spring Boot 3.4, Spring AI |
| AI 模型 | DashScope, Ollama |
| 数据库 | PostgreSQL, pgvector |
| 缓存/限流 | Redis |
| RAG | Markdown/PDF Loader, Token Splitter, pgvector, 增量索引 Manifest |
| 报告 | iText, DOCX XML, Markdown/HTML/SVG |
| 监控 | Actuator, Prometheus, Grafana |
| 部署 | Docker, Docker Compose, Nginx HTTPS 反向代理 |

## 目录说明

```text
ai-agent-frontend/          Vue 3 前端与容器内 Nginx 配置
deploy/                     部署配置，包含 Prometheus、pgvector 初始化、HTTPS Nginx 示例
docs/                       工程文档
scripts/                    压测脚本
src/main/java/              后端 Java 代码
src/main/resources/document RAG Markdown 资料
src/main/resources/finance_pdfs RAG PDF 资料
```

## 环境变量

复制模板：

```bash
cp .env.example .env
```

关键配置：

```env
SPRING_PROFILES_ACTIVE=prod

POSTGRES_PASSWORD=replace-with-strong-db-password
SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/aiagent
REDIS_PASSWORD=replace-with-strong-redis-password

APP_API_KEY=replace-with-strong-api-key
APP_AUTH_JWT_SECRET=replace-with-32-plus-chars-secret
APP_AUTH_SMS_CODE_SECRET=replace-with-32-plus-chars-secret
APP_CORS_ALLOWED_ORIGINS=https://your-domain.com

APP_AUTH_SMS_PROVIDER=http
APP_AUTH_SMS_HTTP_ENDPOINT=https://your-sms-provider.example/send
APP_AUTH_SMS_HTTP_SECRET=replace-with-sms-secret

DASHSCOPE_API_KEY=replace-with-your-dashscope-key
DASHSCOPE_MODEL=qwen-long
EXA_API_KEY=replace-with-your-exa-key
SEARCH_API_KEY=replace-with-your-search-key

APP_RAG_VECTOR_STORE=pgvector
APP_RAG_INDEX_ON_STARTUP=true
APP_RAG_PDF_ENABLED=true

FRONTEND_BIND=127.0.0.1
FRONTEND_PORT=3000
BACKEND_PORT=8123

PROMETHEUS_PORT=6543
GRAFANA_PORT=7654
```

注意：不要提交真实 `.env`。生产环境必须轮换并使用真实强密钥。

## Docker Compose 部署

构建后端：

```bash
./mvnw -q -DskipTests package
```

构建前端：

```bash
cd ai-agent-frontend
npm ci
npm run build
cd ..
```

启动：

```bash
docker compose up -d --build
```

查看状态：

```bash
docker compose ps
```

当前设计下，应用端口默认只绑定本机：

```text
backend -> 127.0.0.1:8123
nginx   -> 127.0.0.1:3000
```

公网用户不应直接访问这些端口，应通过宿主机 Nginx/Caddy 的 `80/443` 反向代理进入。

## HTTPS 入口

生产环境推荐链路：

```text
公网用户 -> https://your-domain.com:443 -> 宿主机 Nginx/Caddy -> http://127.0.0.1:3000 -> Docker 前端 Nginx -> backend:8123
```

宿主机 Nginx 示例：

```text
deploy/nginx-https-example.conf
```

使用时替换：

- `example.com`
- `/etc/letsencrypt/live/example.com/fullchain.pem`
- `/etc/letsencrypt/live/example.com/privkey.pem`

## 健康检查

后端健康检查仅建议本机或内网访问：

```bash
curl http://127.0.0.1:8123/api/actuator/health
```

经前端 Nginx 访问 Actuator 会被阻断，预期返回 `404`：

```bash
curl -i http://127.0.0.1:3000/api/actuator/health
```

前端首页：

```bash
curl -I http://127.0.0.1:3000/
```

## RAG 知识库

资料来源：

```text
src/main/resources/document/**/*.md
src/main/resources/finance_pdfs/**/*.pdf
```

关键配置：

```env
APP_RAG_VECTOR_STORE=pgvector
APP_RAG_INDEX_ON_STARTUP=true
APP_RAG_PDF_ENABLED=true
```

说明：

- 首次部署可保持 `APP_RAG_INDEX_ON_STARTUP=true`，启动时会写入 pgvector。
- 已支持文件 hash 增量索引，未变化资料会跳过 embedding。
- 初次索引完成后，为缩短重启时间，可在生产环境改为 `false`，后续通过专门索引任务处理资料更新。

查询向量表：

```bash
docker compose exec -T postgres psql -U postgres -d aiagent -c "select count(*) from vector_store;"
```

## 监控

Prometheus 和 Grafana 默认仅绑定本机：

```text
Prometheus: http://127.0.0.1:6543
Grafana:    http://127.0.0.1:7654
```

启动：

```bash
docker compose up -d prometheus grafana
```

Grafana 数据源地址：

```text
http://prometheus:9090
```

## 主要接口

### 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/sms/send` | 发送验证码 |
| POST | `/api/auth/sms/login` | 验证码登录 |
| POST | `/api/auth/login` | 密码登录 |
| GET | `/api/auth/me` | 当前用户 |
| PUT | `/api/auth/me` | 修改资料 |
| POST | `/api/auth/avatar` | 上传头像 |

### 会话

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/session/list` | 会话列表 |
| POST | `/api/session/create` | 创建会话 |
| GET | `/api/session/{id}/messages` | 消息列表 |
| POST | `/api/session/{id}/messages` | 写入消息 |
| PUT | `/api/session/{id}/rename` | 重命名 |
| PUT | `/api/session/{id}/pin` | 置顶 |
| DELETE | `/api/session/{id}` | 删除 |

### AI

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/ai/finance_app/chat/sync` | 同步聊天 |
| POST | `/api/ai/finance_app/chat/sse` | SSE 聊天 |
| POST | `/api/ai/finance_app/chat/rag` | RAG 问答 |
| POST | `/api/ai/finance_app/chat/tools` | 工具调用 |
| POST | `/api/ai/manus/chat` | 超级智能体 |

## 安全说明

- 源码中不得提交真实 API Key、JWT 密钥、短信密钥、数据库密码。
- `.env` 已被 `.gitignore` 忽略，生产密钥只放服务器环境变量或密钥管理系统。
- 默认阻断历史测试账号：`demo`、`analyst`、`admin`。
- `schema.sql` 不再初始化固定测试账号。
- 后端和前端容器默认仅绑定 `127.0.0.1`。
- `/api/actuator/**` 经前端 Nginx 访问会返回 `404`。
- Prometheus、Grafana 默认仅绑定本机。
- 资源下载工具禁止跳转下载并限制最大 20MB，降低 SSRF 和超大文件风险。
- 终端工具默认关闭，生产环境不要开启。
- 文件预览/下载已设置 CSP 和 `nosniff`。
- JWT 当前仍由前端保存并通过 `Authorization` 传输，长期建议迁移到 `HttpOnly + Secure + SameSite` Cookie。

## 常用命令

```bash
docker compose ps
docker compose logs -f backend
docker compose logs -f nginx
docker compose config
```

重启后端：

```bash
./mvnw -q -DskipTests package
docker compose up -d --build backend
```

重启前端：

```bash
cd ai-agent-frontend
npm ci
npm run build
cd ..
docker compose up -d --build nginx
```

停止：

```bash
docker compose down
```

不要随意执行 `docker compose down -v`，这会删除数据库、Redis、Grafana、Prometheus 的持久化卷。

## 开发启动

本地开发可显式设置：

```env
SPRING_PROFILES_ACTIVE=local
APP_AUTH_SMS_PROVIDER=mock
```

后端：

```bash
./mvnw spring-boot:run
```

前端：

```bash
cd ai-agent-frontend
npm install
npm run dev
```

## 工程文档

```text
docs/high-concurrency.md
deploy/nginx-https-example.conf
deploy/prometheus.yml
deploy/postgres/init-pgvector.sql
```

## License

MIT

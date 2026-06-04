# AI 金融智能体平台前端

这是一个基于 Vue 3 开发的 AI 金融智能体平台前端，包含 AI 理财顾问和 AI 超级智能体两个核心入口。

## 功能特点

- 💬 **AI 理财顾问**：提供投资、基金、资产配置等金融问答
- 🤖 **AI 超级智能体**：支持工具调用、任务拆解和结构化推理过程展示

## 技术栈

- Vue3
- Vue Router
- Axios
- SSE (Server-Sent Events)

## 开发说明

### 环境要求

- Node.js >= 16.0.0
- npm >= 7.0.0

### 安装依赖

```bash
npm install
```

### 启动开发服务器

```bash
npm run dev
```

### 构建项目

```bash
npm run build
```

## 后端接口

项目依赖以下后端接口：

- `/api/ai/finance_app/chat/sse` - AI 理财顾问聊天接口
- `/api/ai/manus/chat` - AI超级智能体聊天接口
- `/api/session/*` - 会话管理接口
- `/api/hotnews/list` - 热点财经新闻接口

后端服务默认运行在 `http://localhost:8123`

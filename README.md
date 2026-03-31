# SynaptX - 企业级 AI 智能体平台

SynapX 是一个基于大语言模型 (LLM) 和模型上下文协议 (MCP) 的企业级智能 Agent 构建平台。本项目致力于提供一套完整、高可用且安全的 Agent 运行环境，支持复杂的任务编排、多态知识库检索及安全的工具调用。

## 🌟 核心架构与技术亮点

- **Agent 核心工作流**：基于事件驱动架构与 LangChain4j，实现了包含分析 (Analyser)、拆分 (TaskSplit)、执行 (TaskExecution) 和汇总 (Summarize) 的 Agent 计算流，支持复杂任务的动态流转。
- **高阶 RAG 知识引擎**：基于 PostgreSQL + Pgvector 实现向量存储，融合 HyDE（假设性文档嵌入）、Query Expansion（查询扩展）及混合检索与 Rerank 重排策略，大幅提升私有域问答的准确率并降低幻觉。
- **MCP 工具沙箱防泄漏设计**：实现基于 HTTP SSE 协议的 MCP 网关组件。结合 Docker 容器化编排技术，实现了全局审核容器 (Review Container) 与用户级工具沙箱 (User Container) 的强隔离部署，保障第三方 API 及工具调用的安全性。
- **高可用大模型网关**：内置自研 LLM 高可用降级网关服务，支持基于会话亲和性的多 Provider 智能路由与容灾切换，确保模型服务的高可用性。
- **全双工流式通信**：结合 WebSocket 长连接与 JWT 鉴权实现对话消息的高效流式推送 (Streaming)，配合 Next.js 前端体系，提供丝滑的人机交互体验。

## 📁 核心项目结构

- `AgentX/`：后端核心服务，基于 Spring Boot 3 + Java 17 构建。
- `agentx-frontend-plus/`：前端交互界面，基于 Next.js 15 + TypeScript 构建。
- `deploy/`：部署与运维脚手架，包含 Docker Compose 部署脚本及启动环境。
- `docker/`：各类基础组件的 Dockerfile。
- `docs/`：项目文档及架构设计说明。

## 🚀 部署与运行

### 基础环境要求
- Java 17+ (用于后端编译)
- Node.js 18+ 或 pnpm (用于前端构建)
- Docker & Docker Compose (用于环境及容器化部署)
- PostgreSQL (带 pgvector 插件)
- RabbitMQ

### 1. 配置文件准备
进入 `deploy` 目录，复制配置文件模板并配置您的安全密钥、数据库连接及模型 API Key：
```bash
cd deploy
cp .env.example .env
```
> **注意**：生产部署时，请务必修改 `JWT_SECRET` 以及各数据库的默认密码以保障安全性。

### 2. 本地开发与快速调试

#### 后端服务 (AgentX)
```bash
cd AgentX
# 编译
./mvnw clean package -DskipTests
# 启动（使用开发环境配置）
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

#### 前端服务 (agentx-frontend-plus)
```bash
cd agentx-frontend-plus
# 安装依赖
npm install  # 或 pnpm install
# 启动本地开发服务
npm run dev
```
前端默认运行在 `http://localhost:3000`。

### 3. 一键挂载与启动 (Docker环境)
您可以直接使用项目中集成的脚本一键启动包含后端、前端、数据库在内的完整基础环境：
```bash
cd deploy
./start.sh
```

## ⚙️ 核心环境变量说明 (参考)

详见 `deploy/.env` 文件，以下为部分关键配置：

| 配置项 | 说明 | 默认值 |
|--------|------|-------|
| `JWT_SECRET` | 系统 Token 的加密密钥 | `<需随机配置>` |
| `DB_PASSWORD` | PostgreSQL 数据库密码 | `agentx_pass` |
| `RABBITMQ_PASSWORD` | MQ 的连接密码 | `guest` |
| `HIGH_AVAILABILITY_ENABLED` | 是否开启 LLM 路由高可用组件 | `true` |

本平台严格遵循可插拔与云原生设计理念设计，如需定制化与更细粒度的控制，敬请查阅代码仓中的相关内部文档。

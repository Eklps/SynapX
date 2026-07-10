# SynapX - 企业级 AI 智能体平台

SynapX 是一个基于大语言模型 (LLM) 和模型上下文协议 (MCP) 的企业级智能 Agent 构建平台。本项目致力于提供一套完整、高可用且安全的 Agent 运行环境，支持复杂的任务编排、多态知识库检索及安全的工具调用。

## 🌟 核心架构与技术亮点

- **Agent 核心工作流**：基于事件驱动架构与 LangChain4j，实现了包含分析 (Analyser)、拆分 (TaskSplit)、执行 (TaskExecution) 和汇总 (Summarize) 的 Agent 计算流，支持复杂任务的动态流转。
- **高阶 RAG 知识引擎**：基于 PostgreSQL + Pgvector 实现向量存储，融合 HyDE（假设性文档嵌入）、Query Expansion（查询扩展）及混合检索与 Rerank 重排策略，大幅提升私有域问答的准确率并降低幻觉。
- **MCP 工具沙箱防泄漏设计**：实现基于 HTTP SSE 协议的 MCP 网关组件。结合 Docker 容器化编排技术，实现了全局审核容器 (Review Container) 与用户级工具沙箱 (User Container) 的强隔离部署，保障第三方 API 及工具调用的安全性。
- **高可用大模型网关**：内置自研 LLM 高可用降级网关服务，支持基于会话亲和性的多 Provider 智能路由与容灾切换，确保模型服务的高可用性。
- **全双工流式通信**：结合 WebSocket 长连接与 JWT 鉴权实现对话消息的高效流式推送 (Streaming)，配合 Next.js 前端体系，提供丝滑的人机交互体验。

## 📁 核心项目结构

- `AgentX/`：后端核心服务，基于 Spring Boot 3.2.3 + Java 17 构建，监听 `:8088`。
- `agentx-frontend-plus/`：前端交互界面，基于 Next.js 15 + React 19 + TypeScript 构建，监听 `:3000`。
- `deploy/`：部署与运维脚手架，包含 Docker Compose 编排、一键启动脚本 (`start.sh` / `start.bat`) 及环境变量模板。
- `docker/`：各服务的基础镜像 Dockerfile（含 `.dev` 开发变体）。
- `production/`：生产级 Docker Compose 编排。
- `docs/`：项目文档、架构设计说明及 SQL 初始化脚本。

## 🚀 快速开始

### 1. 克隆代码

```bash
git clone https://github.com/Eklps/SynapX.git
cd SynapX
```

### 2. 前置依赖

开始前请确认本机已安装：

- **Git**
- **JDK 17**（项目已自带 Maven Wrapper `mvnw` / `mvnw.cmd`，无需单独安装 Maven）
- **Node.js 18+**（或 pnpm）
- **Docker Desktop + Docker Compose v2**（使用 `docker compose` 插件形式）。**Docker Desktop 必须处于运行状态**。

> 走 Docker 一键部署时，PostgreSQL（带 pgvector）、RabbitMQ 等中间件由 Compose 自动提供，**无需本机预装**。仅在手动跑后端（见下文“本地开发”）时才需要自行准备这些中间件。

### 3. 部署（推荐：Docker 一键启动）

最省事的方式——一条命令拉起前端、后端、Postgres+pgvector、RabbitMQ、Adminer、HA 网关全部组件：

```bash
cd deploy
./start.sh          # Windows 使用 start.bat
```

脚本会自动从 `.env.local.example` 复制 `.env`（若不存在），设置 `DOCKERFILE_SUFFIX=.dev`，并执行 `docker compose --profile local --profile dev up -d --build`。

**首次启动较慢属于正常现象**：后端镜像会在容器内执行 `mvn spring-boot:run`，首次需下载全部 Maven 依赖（含 jitpack 上的 langchain4j SNAPSHOT），约 **1~2 分钟**才能就绪。容器状态显示 "Up" 并不代表已就绪——**请以日志中出现 `Started AgentXApplication` 为准**：

```bash
cd deploy
docker compose logs -f agentx-backend     # 等待 "Started AgentXApplication"
```

就绪后访问 [http://localhost:3000](http://localhost:3000)，使用下方默认账号登录即可。

> ⚠️ **端口提醒**：后端服务端口是 **`:8088`**（非 `:8080`）。个别脚本/旧文档中可能出现 `:8080`，那都是过时的，请一律以 `:8088` 为准。

### 4. 本地开发（手动运行后端 / 前端）

适合需要频繁改动代码的场景。后端具备 devtools 热重启。

**后端服务 (AgentX)**——需本机或某处可连的 PostgreSQL+pgvector 与 RabbitMQ：

```bash
cd AgentX
./mvnw clean package -DskipTests
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**前端服务 (agentx-frontend-plus)**：

```bash
cd agentx-frontend-plus
npm install --legacy-peer-deps     # ⚠️ --legacy-peer-deps 必加（React 19 的 peer dep 冲突）
npm run dev                         # → http://localhost:3000
```

> 小技巧：也可以只用 Docker 起数据库与消息队列（在 `deploy/docker-compose.yml` 里仅启动 `postgres` + `rabbitmq`），后端与前端在宿主机本地运行，兼顾热重载与中间件便利。

### 5. 生产部署

使用 `production/docker-compose.yml`，基于预构建镜像 `ghcr.io/lucky-aeon/agentx:latest`，采用 host 网络模式；HA 网关镜像为 `ghcr.io/lucky-aeon/api-premium-gateway:latest`。

> ⚠️ 生产注意事项：
> - 生产 Compose 默认使用 `postgres:15-alpine`（**不含 pgvector 扩展**），如需 RAG / 向量检索能力，请改用带 pgvector 的 Postgres 镜像。
> - 生产环境**务必修改** `JWT_SECRET`、数据库 / MQ 默认密码，并关闭测试账号（见下方默认账号）。

## 🛠️ 服务访问与管理 (Quick Access)

项目成功启动后，可通过以下地址访问各组件：

| 组件名称 | 访问地址 | 说明 |
| :--- | :--- | :--- |
| **系统前端 (Frontend)** | [http://localhost:3000](http://localhost:3000) | AgentX 用户交互界面 |
| **后端 API (Core API)** | [http://localhost:8088/api](http://localhost:8088/api) | 后端 RESTful API（context-path 为 `/api`） |
| **API 高可用网关** | [http://localhost:8081](http://localhost:8081) | 自研 LLM 路由与分发网关 |
| **数据库管理 (Adminer)** | [http://localhost:8082](http://localhost:8082) | 轻量级数据库管理平台（仅 dev 模式） |
| **消息队列管理** | [http://localhost:15672](http://localhost:15672) | RabbitMQ 控制台 (guest/guest) |
| **PostgreSQL** | `localhost:5432` | 数据库直连地址 |

### 🔑 默认登录账号（仅 dev，生产务必更换）

| 角色 | 账号 | 密码 |
| :--- | :--- | :--- |
| **系统管理员** | `admin@agentx.ai` | `admin123` |
| **测试用户** | `test@agentx.ai` | `test123` |

## ⚙️ 核心环境变量说明

所有配置项在 `AgentX/src/main/resources/application.yml` 中均以 `${ENV:默认值}` 形式声明，可被环境变量覆盖。Docker 部署时通过 `deploy/.env`（从 `.env.local.example` 复制）集中管理。以下为部分关键配置：

| 配置项 | 说明 | 默认值 |
|--------|------|-------|
| `JWT_SECRET` | 系统 Token 的加密密钥（≥32 字符） | `<需随机配置>` |
| `DB_PASSWORD` | PostgreSQL 数据库密码 | `agentx_pass` |
| `RABBITMQ_PASSWORD` | MQ 的连接密码 | `guest` |
| `HIGH_AVAILABILITY_ENABLED` | 是否开启 LLM 路由高可用组件 | `true` |
| `SPRING_PROFILES_ACTIVE` | 运行 profile（`dev` / `prod`） | `dev` |

## 🧯 常见问题与避坑

- **端口对不上？** 后端是 `:8088`，不是 `:8080`。
- **改了 `NEXT_PUBLIC_API_BASE_URL` 不生效？** 该变量在前端代码中当前未被读取，API 地址实际由 `agentx-frontend-plus/lib/api-config.ts` 硬编码，需直接改该文件。
- **MCP 工具调用一直卡住不报错？** MCP 沙箱需要访问宿主机 Docker daemon。Windows 用户需在 Docker Desktop 设置中勾选 *“Expose daemon on tcp://localhost:2375 without TLS”*；排查时请查看 MCP 沙箱容器日志（`docker logs <mcp-sandbox>`），而非仅看后端日志。
- **Docker dev 下前端改了代码不热更新？** 前端源码是构建时 `COPY` 进镜像的，需 `docker compose up -d --build agentx-frontend` 重建；或改用本地 `npm run dev`。
- **手动调用 `docker compose` 报 `./mvnw: not found`？** 需先 `export DOCKERFILE_SUFFIX=.dev`（`start.sh` 已自动设置）。

> 更完整的部署与排错指引，参见项目内的 **deploy-guide** skill（`.agents/skills/deploy-guide/`）及 `AGENTS.md`（经实测验证，较其他文档更可靠）。

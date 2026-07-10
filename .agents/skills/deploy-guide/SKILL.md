---
name: deploy-guide
description: 指引新人把 SynapX / AgentX 从 git clone 到本地跑起来,以及部署到生产。覆盖环境配置、Docker 一键启动、手动 dev、生产部署、服务地址、默认账号,并警示文档中已知的错误和坑。Use whenever the user asks how to deploy / set up / install / run / get started / clone / 配置环境 / 部署 / 怎么跑起来 / 本地启动 this project, or hits a setup or port or Docker or MCP-sandbox issue during onboarding. 项目里现有文档(README/CLAUDE.md/start.sh)存在端口写错、路径过时等问题,本 skill 是权威指引。
---

# 部署 SynapX / AgentX 指引

本项目的部署材料分散在 `README.md`、`AGENTS.md`、`CLAUDE.md`、`deploy/` 中,且**部分文档互相矛盾或已过时**(主要是 `:8088` vs `:8080` 端口错误、CLAUDE.md 引用了不存在的脚本)。**以本 skill 为准**,遇到与本 skill 冲突的文档,信本 skill。

仓库布局:`AgentX/`(后端,Spring Boot 3.2.3,Java 17,`:8088`)、`agentx-frontend-plus/`(前端,Next.js 15.1 + React 19,`:3000`)、`deploy/`(一键启动 + docker-compose)、`docker/`(各服务的 Dockerfile/Dockerfile.dev)、`production/`(生产 compose)。

## 选哪条路径

1. **Docker 一键(推荐,主路径)** — 最省事,一条命令拉起全部(前端、后端、Postgres+pgvector、RabbitMQ、Adminer、HA 网关)。新人优先用这条。
2. **手动 dev** — 后端和前端分别本地跑,适合频繁改后端代码(后端有 devtools 热重启)。仍需自己准备 Postgres+pgvector 和 RabbitMQ(可以只用 Docker 跑 DB/MQ)。
3. **生产** — 用 `production/docker-compose.yml` + 预构建镜像,host 网络模式。

先看【前置依赖】,再看对应路径,务必看【已知坑】。

## 前置依赖(开工前必装)

- **Git**
- **JDK 17**(`AgentX/pom.xml` 锁定 java.version=17)。已自带 Maven Wrapper(`mvnw` / `mvnw.cmd`),系统装不装 Maven 都行。
- **Node.js 18+**(Dockerfile 用 `node:18-alpine`)。pnpm 也行。⚠️ `npm install` 必须加 `--legacy-peer-deps`(React 19 的 peer dep 冲突)。
- **Docker Desktop + Docker Compose v2**(命令是 `docker compose` 插件形式,不是老的 `docker-compose`)。**Docker Desktop 必须处于运行状态**才能跑 `start.sh` / `start.bat`。
- 只有在**不用 Docker、手动跑后端**时,才需要自己装 PostgreSQL(带 pgvector 扩展)和 RabbitMQ。走 Docker 路径则由 compose 提供。

## 路径一:Docker 一键(推荐)

```bash
cd deploy
./start.sh          # Windows: start.bat
```

`start.sh` / `start.bat` 会做这些事:
- 若没有 `.env`,自动从 `.env.local.example` 复制一份;
- `export DOCKERFILE_SUFFIX=.dev`;
- 执行 `docker compose --profile local --profile dev up -d --build`。

**首次启动慢是正常的**:要下载 Maven 依赖(含 jitpack 上的 langchain4j SNAPSHOT),后端容器内执行 `mvn spring-boot:run`,**1~2 分钟后**才就绪。容器状态一直显示 "Up" 不代表 ready——**查日志等 `Started AgentXApplication` 出现**才算起来:

```bash
cd deploy && docker compose logs -f agentx-backend
```

起好后访问 `http://localhost:3000`,用默认账号登录(见下)。

> 手动直接调 `docker compose`(不走 start 脚本)时,**必须先 `export DOCKERFILE_SUFFIX=.dev`**,否则会用生产 Dockerfile,后端构建阶段报 `./mvnw: not found`。

## 路径二:手动 dev(后端 + 前端分别跑)

```bash
# 后端(需要本机或某处有 Postgres+pgvector 和 RabbitMQ 可连)
cd AgentX
./mvnw clean package -DskipTests
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 前端(另开终端)
cd agentx-frontend-plus
npm install --legacy-peer-deps   # --legacy-peer-deps 不能少
npm run dev                       # → http://localhost:3000
```

只想用 Docker 起 DB/MQ、其余本地跑也可以:改 `deploy/docker-compose.yml` 只起 `postgres` + `rabbitmq`,后端的 `DB_HOST`/`RABBITMQ_HOST` 指到 `localhost`。

## 路径三:生产

- 主用 `production/docker-compose.yml`,预构建镜像 `ghcr.io/lucky-aeon/agentx:latest`,**host 网络模式**(DB/MQ 走 `localhost`)。
- HA LLM 网关镜像:`ghcr.io/lucky-aeon/api-premium-gateway:latest`(`:8081`)。
- 另有仓库根 `Dockerfile`(单体镜像,supervisord 在一个容器里跑全栈,profile `docker`)作为单容器备选。
- ⚠️ 生产 compose 用的是 `postgres:15-alpine`(**不含 pgvector**)——RAG/向量功能会挂。生产必须用带 pgvector 的 Postgres(dev compose 用的是正确的 `pgvector/pgvector:pg15`)。
- ⚠️ 生产务必改默认密码、JWT_SECRET,关掉测试账号(见【默认账号】和【已知坑】)。

## 服务地址(以本表为准 —— 端口是 `:8088`,不是 `:8080`)

| 组件 | 地址 |
|---|---|
| 前端 | http://localhost:3000 |
| 后端 API | http://localhost:8088/api(context-path 是 `/api`,故根路径为 `/api`) |
| HA LLM 网关 | http://localhost:8081 |
| Adminer(仅 dev,DB 管理 UI) | http://localhost:8082 |
| RabbitMQ 控制台 | http://localhost:15672(guest/guest) |
| Postgres | localhost:5432 |
| Swagger(若启用) | http://localhost:8088/api/swagger-ui.html |

## 默认账号(仅 dev,生产必须换!)

- 管理员:`admin@agentx.ai` / `admin123`
- 测试用户:`test@agentx.ai` / `test123`

## 关键环境变量

所有值在 `AgentX/src/main/resources/application.yml` 里都是 `${ENV:默认值}` 形式,可被环境变量覆盖。走 Docker 路径时改 `deploy/.env`(从 `.env.local.example` 复制)。主要分组:

- **端口**:`SERVER_PORT`/`BACKEND_PORT`(8088)、`FRONTEND_PORT`(3000)、`POSTGRES_PORT`(5432)、`RABBITMQ_PORT`(5672)、`RABBITMQ_MANAGEMENT_PORT`(15672)、`DEBUG_PORT`(5005)。
- **DB**:`DB_HOST`(compose 内是 `postgres`)、`DB_PORT`、`DB_NAME`(`agentx`)、`DB_USER`(`agentx_user`)、`DB_PASSWORD`(`agentx_pass`)。
- **MQ**:`RABBITMQ_HOST`、`RABBITMQ_PORT`、`RABBITMQ_USERNAME`(`guest`)、`RABBITMQ_PASSWORD`(`guest`)。
- **安全**:`JWT_SECRET`(至少 32 字符;默认值不安全,生产必改)。
- **HA 网关**:`HIGH_AVAILABILITY_ENABLED`、`HIGH_AVAILABILITY_GATEWAY_URL`(compose 内 `http://api-gateway:8081`,从宿主机访问 `http://localhost:8081`)。
- **容器/MCP**:`AGENTX_CONTAINER_DOCKER_HOST`(见【已知坑】#3)。
- **Spring**:`SPRING_PROFILES_ACTIVE`(`dev`/`prod`)、`JPA_DDL_AUTO`(`update`/`validate`)。
- LLM 厂商 API key 等可选,首次起来后在应用内配置即可。

## 已知坑(本项目部署最容易踩的 —— 重点看)

1. **后端端口是 `:8088`,不是 `:8080`。** `deploy/start.sh` 结尾 echo、`deploy/README.md`、`CLAUDE.md` 都写成了 `:8080`,**全是错的**。真相:`application.yml` 的 `SERVER_PORT:8088`、`docker-compose.yml` 映射 `8088:8088`、前端 `lib/api-config.ts` 硬编码 `:8088/api`。**到处认 `:8088`。**

2. **`NEXT_PUBLIC_API_BASE_URL` 这个环境变量当前是无效的。** 前端的 `agentx-frontend-plus/lib/api-config.ts` 里 `getDefaultApiUrl()` 硬编码了 URL:`localhost`/`127.0.0.1` → `${protocol}//${hostname}:8088/api`,否则 → `/api`。代码里**没有任何地方读** `process.env.NEXT_PUBLIC_API_BASE_URL`。compose 和根 Dockerfile 会传这个变量,但运行时没人用。**要改 API 地址得改 `api-config.ts`,改 env 没用。**

3. **MCP 工具沙箱需要访问宿主机 Docker daemon。** 后端容器挂载 `/var/run/docker.sock`(Linux/Mac),Windows 则走 `tcp://host.docker.internal:2375`(`AGENTX_CONTAINER_DOCKER_HOST` 设为此值)。**Windows 需要在 Docker Desktop 设置里勾选 "Expose daemon on tcp://localhost:2375 without TLS"**。没有 Docker daemon 访问权限时,核心聊天仍可用,但 MCP 工具沙箱建不起来(因为后端在容器里,得通过宿主 Docker 去创建沙箱容器)。沙箱容器用**命名卷** `mcp-user-<userId>` / `mcp-review-system`,不能用 bind mount(后端在容器内,其文件系统路径对宿主 Docker 不可见)。

4. **首次构建慢是正常的。** dev 后端 Dockerfile 已配阿里云 Maven 镜像加速,但首次仍要下 langchain4j SNAPSHOT(jitpack)。约 1~2 分钟。在此期间**不会上报 healthy**,要靠日志里出现 `Started AgentXApplication` 判断就绪:`cd deploy && docker compose logs -f agentx-backend`。

5. **Docker dev 下前端没有热重载。** `deploy/docker-compose.yml` 的 `agentx-frontend` 服务没有 `volumes:`,源码是构建时 `COPY` 进去的。改前端代码要生效:要么 `docker cp <文件> agentx-frontend:/app/<路径>`(快,但 rebuild 会丢),要么 `docker compose up -d --build agentx-frontend`(持久)。后端有 devtools 热重启。

6. **手动调 `docker compose` 必须先 `export DOCKERFILE_SUFFIX=.dev`**(不走 start 脚本时)。否则用生产 Dockerfile,后端报 `./mvnw: not found`。start 脚本已替你设好。

7. **`CLAUDE.md` 的部署章节有过时路径,别照着走。** `bin/start-dev.sh`、`bin/start-dev.bat`、`script/setup_with_compose.sh`、`docker-compose.dev.yml` **都不存在**。真实入口只有 `deploy/start.sh` / `deploy/start.bat`。CLAUDE.md 里的 `./mvn spring-boot:run` 也写错了(应是 `./mvnw`)。

8. **`docs/deployment/` 目录不存在。** start 脚本里引用的 `PRODUCTION_DEPLOY.md`、`TROUBLESHOOTING.md` 都是死链,别给用户指过去。

9. **`application-dev.yml` 里有个机器绝对路径地雷。** `FILE_STORAGE_PATH` 默认是 `E:/agentProject/myAgent/ragRepo/`(硬编码的 Windows 绝对路径)。换机器必须用环境变量 `FILE_STORAGE_PATH` 覆盖,否则本地文件存储会失败。

10. **MCP 的"卡住"可能不是报错而是 1 小时超时。** 运行时 MCP 客户端(`AgentToolManager.java`)设了 **1 小时**超时。一个失败/挂起的 MCP server 表现为"无限卡住"而非报错。排查 MCP "stuck" 时,怀疑沙箱容器里的 stdio 子进程(npx),看 `docker logs <mcp-sandbox>` + 容器内 `logs/<tool>.log`,别只看后端日志。

11. **生产 compose 的 Postgres 没有 pgvector**(`postgres:15-alpine`),见【路径三】。dev 用的 `pgvector/pgvector:pg15` 是对的。

12. **`npm install` 必须加 `--legacy-peer-deps`**(React 19 peer dep 冲突)。两个前端 Dockerfile 都加了;手动装时别忘了。

## 常用验证命令(在 `deploy/` 下执行)

```bash
docker compose ps                          # 查状态
docker compose logs -f agentx-backend      # 跟后端日志(看 Started AgentXApplication)
docker compose down                        # 停掉全部
docker compose restart <服务名>             # 重启单个服务
docker compose up -d --build agentx-frontend   # 改完前端代码后重建
```

## 指向真实文件(需要细节时去读)

- 一键脚本:`deploy/start.sh`、`deploy/start.bat`
- compose 定义:`deploy/docker-compose.yml`(服务、端口、卷、健康检查、profile)
- 环境变量模板:`deploy/.env.local.example`(dev)、`deploy/.env.production.example`(自带 DB 生产)、`deploy/.env.external.example`(外接 DB 生产)、仓库根 `.env.example`(单体镜像)
- 后端配置:`AgentX/src/main/resources/application.yml` + `application-dev.yml`
- 前端 API 地址真相:`agentx-frontend-plus/lib/api-config.ts`
- 各服务镜像构建:`docker/backend/Dockerfile[.dev]`、`docker/frontend/Dockerfile[.dev]`
- 生产 compose:`production/docker-compose.yml`;单体镜像:仓库根 `Dockerfile`
- 项目级操作备忘(经实测验证):`AGENTS.md`(比 CLAUDE.md 可靠)

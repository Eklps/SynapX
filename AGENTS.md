# Repository Guidelines

## Project Purpose
SynapX (code package `org.xhy`, artifact `agent-x`) is an enterprise-grade AI agent platform built on LLMs and MCP (Model Context Protocol). Core capabilities: an event-driven Agent workflow (Analyser → TaskSplit → TaskExecution → Summarize, powered by LangChain4j), a PostgreSQL + pgvector RAG engine (HyDE, query expansion, hybrid retrieval + rerank), an MCP tool sandbox (HTTP-SSE gateway with per-user Docker isolation), a self-built high-availability LLM gateway with multi-provider failover, and full-duplex WebSocket streaming with JWT auth.

## Project Structure & Module Organization
- `AgentX/` — Spring Boot 3.2.3 backend, Java 17, Maven Wrapper. Source `AgentX/src/main`, tests `AgentX/src/test`. Group `org.xhy`, artifact `agent-x`. Backend listens on **:8088**; the HA LLM gateway on **:8081**.
- `agentx-frontend-plus/` — Next.js 15.1 + React 19 + TypeScript + Tailwind + shadcn/Radix UI. App routes under `app/`, shared UI in `components/`, hooks in `hooks/`, lib in `lib/`, types in `types/`.
- `deploy/` — Docker Compose deployment + `start.sh` / `start.bat` one-click launch.
- `docker/` — base Dockerfiles (`backend/`, `frontend/`).
- `docs/` — design docs (`docs/myDocs/`, `docs/sql/`, `docs/monitoring/`, `docs/billing/`).
- `production/` — production-grade `docker-compose.yml`.
- `logs/`, `backend.log`, `docker_backend.log` — runtime/build logs (gitignored, do not commit).

## Build, Test, and Development Commands
- Backend (from `AgentX`):
  - Build: `./mvnw -DskipTests package`
  - Run (dev): `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`
  - Test: `./mvnw test`
  - Format: `./mvnw spotless:apply`
- Frontend (from `agentx-frontend-plus`):
  - Install deps: `npm install` (or `pnpm install`)
  - Dev server: `npm run dev` → http://localhost:3000
  - Build/Start: `npm run build && npm start`
  - Lint: `npm run lint` (Next.js lint)
- All‑in‑one (Docker): `cd deploy && ./start.sh` (Windows: `start.bat`) to spin up backend, frontend, DB, MQ.

## Related Docs
- `README.md` — quickstart, service URLs (frontend :3000, API :8088, gateway :8081, Adminer :8082, RabbitMQ console :15672), env-var reference.
- `CLAUDE.md` — large, detailed architecture/feature reference; read before touching sensitive areas (auth, MCP gateway, RAG, billing).
- `TODO.md` — roadmap/in-progress work.

## Local Docker Dev — Operational Notes (verified)
These facts were confirmed by actually running `deploy/start.sh`; they are easy to get wrong:
- **All-in-one launch**: `cd deploy && ./start.sh` (Windows: `start.bat`). It runs `docker compose --profile local --profile dev up -d --build`. Requires Docker Desktop running **and** network access to pull base images.
- **Backend port is :8088, not :8080.** `deploy/start.sh` *prints* "后端API: http://localhost:8080" at the end — that message is wrong. The actual mapping in `docker-compose.yml` is `8088:8088`, matching `application.yml` (`SERVER_PORT:8088`) and the frontend's `NEXT_PUBLIC_API_BASE_URL` (`http://localhost:8088/api`). Trust :8088, ignore the script's :8080 line.
- **Backend image builds at container start, not image build time.** The dev Dockerfile runs `mvn spring-boot:run` inside the container, so the first start downloads all Maven deps (incl. a langchain4j SNAPSHOT from jitpack) and can take 1–2 min before Spring Boot is ready. No `healthy` state is reported until then — poll the logs for `Started AgentXApplication` / scheduled-task activity rather than trusting the container "Up" status.
- **Frontend runs `npm run dev` but source is NOT volume-mounted.** `docker-compose.yml`'s `agentx-frontend` service has no `volumes:`; the dev Dockerfile `COPY`s source at build time. Consequence: editing source on the host does **not** hot-reload the container. To apply a frontend change to the running dev env, either (a) `docker cp <file> agentx-frontend:/app/<path>` for a fast hot-reload (lost on `docker compose down`/rebuild), or (b) `docker compose up -d --build agentx-frontend` to rebuild the image (persistent). Make sure `DOCKERFILE_SUFFIX=.dev` is exported when invoking compose directly, otherwise the production Dockerfile is used and the backend stage will fail (`./mvnw: not found`).
- **Default dev accounts** (rotate in prod): admin `admin@agentx.ai` / `admin123`; user `test@agentx.ai` / `test123`.
- Useful commands (from `deploy/`): `docker compose ps` (status), `docker compose logs -f agentx-backend` (logs), `docker compose down` (stop), `docker compose restart <svc>` (restart one).

## Architecture & Layer Rules (Backend DDD)
Backend code follows a strict 4-layer DDD layout under `src/main/java/org/xhy/`. Keep dependencies pointing inward (domain → never depends on interfaces/application):
- `domain/` — business core, domain services & entities. Major bounded contexts: `agent`, `conversation`, `rag`, `llm`, `tool`, `task`, `memory`, `container`, `highavailability`, `user`, `order`, `payment`, `billing`, `trace`. LangChain4j integration lives in services here (e.g. `rag/HyDEDomainService`, `agent/SystemPromptGeneratorDomainService`).
- `application/` — orchestration/use-case layer per context (mirrors domain contexts); depends on `domain`.
- `infrastructure/` — technical adapters: `mcp_gateway/`, `docker/`, `mq/` (RabbitMQ), `rag/`, `llm/`, `transport/`, `terminal/`, `repository/` (MyBatis-Plus), `auth/`, `ratelimit/`, `initializer/`, `config/`.
- `interfaces/` — inbound adapters only: `api/` (REST controllers) and `dto/`.
- DB migrations live in `src/main/resources/db/migration` + `schema`. Config via `application.yml` / `application-dev.yml`; all values overridable by env vars (`${ENV:default}`).

## Coding Style & Naming Conventions
- Java (backend): formatted via Spotless (`./mvnw spotless:apply`) using `AgentX/eclipse-formatter.xml`. 4‑space indent, PascalCase classes, camelCase methods/fields, packages `org.xhy.<layer>.<context>`. Spring Boot 3.2.3 + Java 17.
- TypeScript/React (frontend): 2‑space indent, React components PascalCase, files kebab‑case (e.g., `model-select-dialog.tsx`). Keep hooks in `hooks/`, UI in `components/`, types in `types/`. Route groups are parenthesized Next.js layout segments: `(auth)/`, `(main)/`, `widget/[publicId]`.
- Run linters/formatters before pushing; keep functions small and cohesive.

## Testing Guidelines
- Backend: JUnit via Spring Boot starter. Place tests under `AgentX/src/test/java`, name `*Test.java`. Run with `./mvnw test` and target service/controller layers.
- Frontend: No default test runner configured; for UI changes include a minimal test plan or interactive demo steps in the PR.

## Commit & Pull Request Guidelines
- Use Conventional Commits: `feat(scope): ...`, `fix(scope): ...`, `refactor: ...`, `docs: ...`, `chore: ...`.
- PRs include: clear description, linked issues (`Fixes #123`), test plan (commands + expected result), and screenshots/GIFs for UI.
- Keep PRs small and focused; update docs when behavior changes.

## Security & Configuration Tips
- Copy `.env.example` to `.env` (template at repo root; consumed by `deploy/`) and set secure values (`JWT_SECRET`, DB/MQ passwords, LLM API keys). Never commit secrets.
- Default dev accounts (dev only, rotate in prod): admin `admin@agentx.ai` / `admin123`; user `test@agentx.ai` / `test123`.
- Key env knobs: `SERVER_PORT` (8088), `HIGH_AVAILABILITY_ENABLED`, `DB_PASSWORD`, `RABBITMQ_PASSWORD`, plus provider API keys. All are wired as `${ENV:default}` in `application.yml`.
- Prefer `deploy/start.sh` for a consistent local environment. Review `production/docker-compose.yml` and `docs/` for production hardening.

## Agent Type System (runtime execution modes, NOT a persisted enum)
There is **no `AgentEntity.type` field and no PREVIEW/CHAT/COMPLEX/MCP enum**. The "4 agent kinds" are runtime execution modes dispatched by endpoint + request DTO + widget config:
- **Preview** — `PreviewMessageHandler` (`application/conversation/service/message/preview/`), endpoint `POST /agents/sessions/preview`, body `AgentPreviewRequest`. Does **not persist** messages (uses a virtual `preview-agent`).
- **Chat / Complex task** — `AgentMessageHandler` (`application/conversation/service/message/agent/`) is the **default** handler for `POST /agents/sessions/chat` with a plain `ChatRequest`. Dispatch hub: `MessageHandlerFactory.getHandler()` (`application/conversation/service/handler/`).
- **RAG** — `RagMessageHandler`, selected when the DTO is `RagChatRequest` (`POST /rag/search/stream-chat`) or a widget with `widgetType=RAG`.
- **MCP** — **not a type, a capability** layered onto Preview/Agent handlers when `toolIds` resolve to MCP servers (see next section).

Two traps to know:
- `ChatMessageHandler` (`application/conversation/service/message/chat/`) is **dead code** — registered as a bean but never selected by `MessageHandlerFactory`; plain chat runs through `AgentMessageHandler`.
- The **complex task-splitting workflow** (`AnalyserMessageHandler` → `TaskSplitHandler` → `TaskExecutionHandler` → `SummarizeHandler`, under `application/conversation/service/message/agent/{handler,workflow,event}/`) is **fully implemented but not wired into the live chat path** — no `new AgentWorkflowContext(...)` or `AgentEventBus.register(...)` call sites exist. So "complex task" agents today behave as ordinary tool-augmented chat; task splitting does not trigger.

## MCP Tool Chain (end-to-end, verified)
- **`toolIds` convention**: `AgentEntity.toolIds` / request `toolIds` hold **`tools.id`** (the tool definition id), NOT `user_tools.id`. Resolution: `UserToolDomainService.getInstallTool()` queries `user_tools.tool_id IN (toolIds)` (`domain/tool/service/UserToolDomainService.java:75`); a mismatch throws `使用的工具不存在`.
- **Execution path** (7 layers): backend → `AgentToolManager.createToolProvider()` (`application/conversation/service/message/agent/AgentToolManager.java`) → `McpUrlProviderService` resolves `mcp_server_name` → `MCPGatewayService.deployTool` (`infrastructure/mcp_gateway/`) POSTs to the user sandbox container → gateway runs the tool's `install_command` (e.g. `npx @modelcontextprotocol/server-filesystem /app/storage`) → stdio-SSE bridge → langchain4j `McpToolProvider`.
- **Sandbox containers** are created by `DockerService` (`infrastructure/docker/`) via Docker socket. Dev compose mounts `host.docker.internal:2375` into the backend — the backend **needs Docker daemon access** for MCP to work at all.
- **Timeout gotcha**: the runtime MCP client (`AgentToolManager.java:51-52`) sets a **1-hour** timeout. A failed/hung MCP server therefore appears as an indefinite "hang", not an error. When debugging MCP "stuck" issues, suspect the stdio child (`npx` process inside the sandbox container) and check `docker logs <mcp-sandbox>` + the container's `logs/<tool>.log`, not just backend logs.
- **Volume (Docker-in-Docker)**: sandbox containers mount a **named volume** `mcp-user-<userId>` (USER) / `mcp-review-system` (REVIEW) at `/app/storage`. Container templates (`domain/container/service/ContainerTemplateDomainService.java`, `createBuiltinMcpGatewayTemplate`/`createBuiltinReviewContainerTemplate`) define this. Named volumes (not bind mounts) are required because the backend runs inside a container and its filesystem paths are invisible to the host Docker daemon.


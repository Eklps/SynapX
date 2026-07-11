# Chat 模式工具调用 & 思考折叠展示 - 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把普通 chat 模式（`chat-panel.tsx`）的工具调用和思考过程，改造成类似图二的"思考 N 次，查看 M 个文件 >"可折叠块样式；trace 详情页同步改造。

**Architecture:** 后端拆出独立 `THINKING_*` 消息类型 + 用 `TOOL_CALL_GROUP_END` 携带工具调用汇总（通过 `AgentChatResponse.payload` 字段传 JSON），`MessageEntity.metadata` 持久化；前端新增 `ThinkingToolCallGroup` 折叠组件，参考 RAG 的 `rag-chat/ThinkingProcess.tsx` 风格。

**Tech Stack:**
- 后端：Spring Boot 3.2.3、Java 17、LangChain4j（已有 `onPartialReasoning` API）
- 前端：Next.js 15 + React 19 + TypeScript + Tailwind + shadcn/ui `Collapsible`
- 消息序列化：复用 `AgentChatResponse.payload`（已有）

## 全局约束

- 后端 NO Lombok，使用标准 getter/setter
- 后端格式化：提交前跑 `.\mvnw.cmd spotless:apply`
- 前端命名：组件 PascalCase、变量 camelCase
- 后端不可破坏现有 RAG 聊天（已有 `RAG_THINKING_*` 类型，本次新增的 `THINKING_*` 是不带 `RAG_` 前缀的独立类型）
- 不可修改 `components/rag-chat/*` 和 `components/agent-preview-chat.tsx`（明确排除范围）
- 所有修改必须向后兼容：历史消息无 `metadata` 字段也能正常展示
- 每完成一个 Task 跑一次 `.\mvnw.cmd compile` 验证后端不破
- 前端改完跑 `npm run build` 验证 TS 不破

## 文件结构

| 文件 | 责任 | 动作 |
|---|---|---|
| `AgentX/.../domain/conversation/constant/MessageType.java` | 消息类型枚举 | 修改：新增 4 个枚举值 |
| `AgentX/.../application/conversation/service/message/AbstractMessageHandler.java` | 普通 chat 流处理 | 修改：加 `onPartialReasoning` + 工具调用累计 + metadata 持久化 |
| `agentx-frontend-plus/components/agent/ThinkingToolCallGroup.tsx` | 折叠展示组件 | 新建 |
| `agentx-frontend-plus/components/chat-panel.tsx` | 普通 chat 面板 | 修改：消息结构 + 流式累积 + 渲染 |
| `agentx-frontend-plus/app/(main)/traces/agents/[agentId]/sessions/[sessionId]/page.tsx` | trace 详情页 | 修改：把单条 TOOL_CALL 折叠成组 |

---

### Task 1: 后端 `MessageType` 扩展

**Files:**
- Modify: `AgentX/src/main/java/org/xhy/domain/conversation/constant/MessageType.java`

**Interfaces:**
- Consumes: 现有 `MessageType` 枚举
- Produces: 新增 4 个枚举值（`THINKING_START`/`THINKING_PROGRESS`/`THINKING_END`/`TOOL_CALL_GROUP_END`），供后续 Task 2 引用

- [ ] **Step 1: 修改 `MessageType.java`**

在现有枚举末尾的 `RAG_ANSWER_END` 之后追加 4 个新枚举值：

```java
package org.xhy.domain.conversation.constant;

/** 消息类型枚举 */
public enum MessageType {
    /** 普通文本消息 */
    TEXT,

    /** 工具调用消息 */
    TOOL_CALL,

    /** 任务执行消息 */
    TASK_EXEC,
    /** 任务状态进行中 */
    TASK_STATUS_TO_LOADING,

    /** 任务状态完成 */
    TASK_STATUS_TO_FINISH,

    /** 任务拆分结束消息 */
    TASK_SPLIT_FINISH,

    /** RAG检索开始 */
    RAG_RETRIEVAL_START,

    /** RAG检索进行中 */
    RAG_RETRIEVAL_PROGRESS,

    /** RAG检索结束 */
    RAG_RETRIEVAL_END,

    /** RAG思考开始 */
    RAG_THINKING_START,

    /** RAG思考进行中 */
    RAG_THINKING_PROGRESS,

    /** RAG思考结束 */
    RAG_THINKING_END,

    /** RAG回答开始 */
    RAG_ANSWER_START,

    /** RAG回答进行中 */
    RAG_ANSWER_PROGRESS,

    /** RAG回答结束 */
    RAG_ANSWER_END,

    /** 普通 chat 思考开始（由 onPartialReasoning 触发） */
    THINKING_START,

    /** 普通 chat 思考增量 */
    THINKING_PROGRESS,

    /** 普通 chat 思考结束 */
    THINKING_END,

    /** 工具调用组结束（payload 字段携带 JSON 汇总：count、fileCount、toolNames） */
    TOOL_CALL_GROUP_END
}
```

注意：与 RAG 区分 —— `THINKING_*`（不带 RAG 前缀）用于普通 chat 流。

- [ ] **Step 2: 编译验证**

Run: `.\mvnw.cmd compile -q -DskipTests`
Expected: BUILD SUCCESS（无输出说明成功）

- [ ] **Step 3: 提交**

```bash
git add AgentX/src/main/java/org/xhy/domain/conversation/constant/MessageType.java
git commit -m "feat(chat): 新增 THINKING_* 和 TOOL_CALL_GROUP_END 消息类型"
```

---

### Task 2: `AbstractMessageHandler` 普通 chat 流改造

**Files:**
- Modify: `AgentX/src/main/java/org/xhy/application/conversation/service/message/AbstractMessageHandler.java`（约 380-475 行区域）

**Interfaces:**
- Consumes: Task 1 新增的 `MessageType.THINKING_*` / `MessageType.TOOL_CALL_GROUP_END`
- Produces: 
  - `onPartialReasoning` 推送 `THINKING_START/PROGRESS/END`
  - `onToolExecuted` 累加计数（不存独立 toolMessage）
  - `onCompleteResponse` 推 `TOOL_CALL_GROUP_END`（payload 携 JSON）+ 把 metadata 写入 llmEntity

**关键决策**：
- 移除 `onToolExecuted` 里的独立 `toolMessage` 持久化（`messageDomainService.saveMessageAndUpdateContext(toolMessage, ...)`）和单独的 `TOOL_CALL` 推流
- 中断时 `onError` 仍然保留 "保存累积内容" 的逻辑（如果 `messageBuilder` 非空），同时记录 metadata

- [ ] **Step 1: 在 `processStreamingChat` 方法开头增加状态变量**

定位到 `AbstractMessageHandler.java` 的 `processStreamingChat` 方法（约 350 行 `protected <T> void processStreamingChat(...)` 之后），在 `tokenStream.onError` 之前插入：

```java
        // 思维链状态跟踪
        final boolean[] thinkingStarted = {false};
        final boolean[] thinkingEnded = {false};
        final boolean[] hasThinkingProcess = {false};

        // 工具调用累积
        final java.util.concurrent.atomic.AtomicInteger toolCallCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.concurrent.atomic.AtomicInteger fileViewCount = new java.util.concurrent.atomic.AtomicInteger(0);
        final java.util.List<String> toolNames = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        final StringBuilder thinkingContentBuilder = new StringBuilder();

        // 判断工具名是否算"查看文件"
        // 知识库检索、读文件类工具算；其他不算
        java.util.function.Predicate<String> isFileViewTool = name ->
            name != null && (name.contains("knowledge") || name.contains("search")
                || name.contains("file_read") || name.contains("read_file"));
```

- [ ] **Step 2: 替换 `onPartialResponse`，加上 `onPartialReasoning`**

定位 `tokenStream.onPartialResponse(reply -> { ... });`（约 383 行），**替换整个回调块**为：

```java
        // 思维链处理
        tokenStream.onPartialReasoning(reasoning -> {
            hasThinkingProcess[0] = true;
            if (!thinkingStarted[0]) {
                transport.sendMessage(connection,
                        AgentChatResponse.build("开始思考...", MessageType.THINKING_START));
                thinkingStarted[0] = true;
            }
            thinkingContentBuilder.append(reasoning);
            transport.sendMessage(connection,
                    AgentChatResponse.build(reasoning, MessageType.THINKING_PROGRESS));
        });

        // 部分响应处理
        tokenStream.onPartialResponse(reply -> {
            // 【策略B：立即短路】检测中断信号，需在吸收每个Token时判断
            InterruptStrategy strategy = chatContext.getAgent().getInterruptStrategy();
            if (strategy == InterruptStrategy.IMMEDIATE
                    && chatSessionManager.isSessionInterrupted(chatContext.getSessionId())) {
                String partialContent = messageBuilder.get().toString();
                logger.info("策略B：检测到中断信号，准备短路中止: sessionId={}, 已生成内容长度={}", chatContext.getSessionId(),
                        partialContent.length());
                // 持久化已生成的部分内容（如果有的话）
                if (!partialContent.isBlank()) {
                    llmEntity.setContent(partialContent + "[已中断]");
                    messageDomainService.saveMessageAndUpdateContext(Collections.singletonList(llmEntity),
                            chatContext.getContextEntity());
                }
                // 抛出受控异常，触发 onError 回调并被静默处理
                throw new RuntimeException("USER_INTERRUPTED");
            }

            // 如果有思考过程但还没结束思考，先结束思考阶段
            if (hasThinkingProcess[0] && !thinkingEnded[0]) {
                transport.sendMessage(connection,
                        AgentChatResponse.build("思考完成", MessageType.THINKING_END));
                thinkingEnded[0] = true;
            }

            // 如果没有思考过程且还没开始过思考，先发送思考开始和结束
            if (!hasThinkingProcess[0] && !thinkingStarted[0]) {
                transport.sendMessage(connection,
                        AgentChatResponse.build("开始思考...", MessageType.THINKING_START));
                transport.sendMessage(connection,
                        AgentChatResponse.build("思考完成", MessageType.THINKING_END));
                thinkingStarted[0] = true;
                thinkingEnded[0] = true;
            }

            messageBuilder.get().append(reply);
            // 删除换行后消息为空字符串
            if (messageBuilder.get().toString().trim().isEmpty()) {
                return;
            }

            // 直接发送消息，transport内部处理连接异常
            transport.sendMessage(connection, AgentChatResponse.build(reply, MessageType.TEXT));
        });
```

- [ ] **Step 3: 改造 `onToolExecuted`，累加计数、移除独立持久化**

定位 `tokenStream.onToolExecuted(toolExecution -> { ... });`（约 450-472 行），**替换整个回调块**为：

```java
        // 工具执行处理
        tokenStream.onToolExecuted(toolExecution -> {
            // 累加工具调用统计
            String toolName = toolExecution.request().name();
            toolCallCount.incrementAndGet();
            if (toolName != null) {
                toolNames.add(toolName);
                if (isFileViewTool.test(toolName)) {
                    fileViewCount.incrementAndGet();
                }
            }

            // 调用工具调用完成钩子
            ToolCallInfo toolCallInfo = buildToolCallInfo(toolExecution);
            onToolCallCompleted(chatContext, toolCallInfo);
        });
```

注意：**移除**了原来的：
- `if (!messageBuilder.get().isEmpty()) { ... saveMessageAndUpdateContext(llmEntity) ... }`（这部分由 `onCompleteResponse` 统一处理）
- 独立的 `toolMessage` 持久化
- 单独的 `TOOL_CALL` 推流

- [ ] **Step 4: 改造 `onCompleteResponse`，发送 `TOOL_CALL_GROUP_END` + 写入 metadata**

定位 `tokenStream.onCompleteResponse(chatResponse -> { ... });`（约 412 行附近），**在方法体最开头**（`this.setMessageTokenCount(...)` 之前）插入：

```java
        // 完整响应处理
        tokenStream.onCompleteResponse(chatResponse -> {

            // 兜底：如果到结束还没发出 THINKING_END，补发一个
            if (hasThinkingProcess[0] && !thinkingEnded[0]) {
                transport.sendMessage(connection,
                        AgentChatResponse.build("思考完成", MessageType.THINKING_END));
                thinkingEnded[0] = true;
            }

            // 把思考内容、工具调用汇总写入 llmEntity.metadata（JSON）
            try {
                com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.LinkedHashMap<String, Object> meta = new java.util.LinkedHashMap<>();
                if (thinkingContentBuilder.length() > 0) {
                    meta.put("thinkingContent", thinkingContentBuilder.toString());
                }
                if (toolCallCount.get() > 0) {
                    java.util.LinkedHashMap<String, Object> tcg = new java.util.LinkedHashMap<>();
                    tcg.put("count", toolCallCount.get());
                    tcg.put("fileCount", fileViewCount.get());
                    tcg.put("toolNames", new java.util.ArrayList<>(toolNames));
                    meta.put("toolCallGroup", tcg);

                    // 推流：TOOL_CALL_GROUP_END，payload 携带同一份 JSON 供前端流式消费
                    String payload = om.writeValueAsString(tcg);
                    AgentChatResponse groupEnd = AgentChatResponse.buildEndMessage(
                            "执行了 " + toolCallCount.get() + " 个工具", MessageType.TOOL_CALL_GROUP_END);
                    groupEnd.setPayload(payload);
                    transport.sendMessage(connection, groupEnd);
                }
                if (!meta.isEmpty()) {
                    llmEntity.setMetadata(om.writeValueAsString(meta));
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                logger.warn("构造 toolCallGroup metadata 失败: {}", e.getMessage());
            }

            this.setMessageTokenCount(chatContext.getMessageHistory(), userEntity, llmEntity, chatResponse);
```

（注意：方法体余下部分保持原样不动。）

- [ ] **Step 5: 编译验证**

Run: `.\mvnw.cmd compile -q -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 6: 格式化**

Run: `.\mvnw.cmd spotless:apply -q`
Expected: 无输出（成功）

- [ ] **Step 7: 再次编译**

Run: `.\mvnw.cmd compile -q -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 8: 提交**

```bash
git add AgentX/src/main/java/org/xhy/application/conversation/service/message/AbstractMessageHandler.java
git commit -m "feat(chat): 普通 chat 流支持思考链 + 工具调用汇总推送，metadata 持久化"
```

---

### Task 3: 前端 `ThinkingToolCallGroup` 组件

**Files:**
- Create: `agentx-frontend-plus/components/agent/ThinkingToolCallGroup.tsx`

**Interfaces:**
- Consumes:
  - `thinkingContent?: string`
  - `isThinkingComplete?: boolean`
  - `toolCallGroup?: { count: number; fileCount: number; toolNames: string[] }`
- Produces: 默认折叠的展示组件，标题"思考已完成，查看 M 个文件"或"思考已完成，调用 N 个工具"

- [ ] **Step 1: 创建文件**

在 `agentx-frontend-plus/components/agent/` 目录新建 `ThinkingToolCallGroup.tsx`：

```tsx
"use client";

import { Brain, ChevronRight, Wrench, FileText } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { MessageMarkdown } from "@/components/ui/message-markdown";

export interface ToolCallGroup {
  count: number;
  fileCount: number;
  toolNames: string[];
}

interface ThinkingToolCallGroupProps {
  thinkingContent?: string;
  isThinkingComplete?: boolean;
  toolCallGroup?: ToolCallGroup;
}

/**
 * 普通 chat 模式的思考过程 + 工具调用汇总折叠组件
 * 默认折叠；展开后显示思考详情和工具调用列表
 */
export function ThinkingToolCallGroup({
  thinkingContent,
  isThinkingComplete,
  toolCallGroup,
}: ThinkingToolCallGroupProps) {
  // 没有内容就不渲染
  if (!thinkingContent && !toolCallGroup) {
    return null;
  }

  // 标题构造：思考状态 + 文件/工具汇总
  const titleParts: string[] = [];
  if (thinkingContent) {
    titleParts.push(isThinkingComplete ? "思考已完成" : "思考中");
  }
  if (toolCallGroup && toolCallGroup.count > 0) {
    if (toolCallGroup.fileCount > 0) {
      titleParts.push(`查看 ${toolCallGroup.fileCount} 个文件`);
    } else {
      titleParts.push(`调用 ${toolCallGroup.count} 个工具`);
    }
  }
  const title = titleParts.length > 0 ? titleParts.join("，") : "处理过程";

  return (
    <Collapsible defaultOpen={false} className="group w-full">
      <Card className="px-4 py-2 bg-purple-50 dark:bg-purple-950/20 mb-2 border-purple-200 dark:border-purple-800">
        <CollapsibleTrigger className="flex items-center justify-between w-full text-left hover:opacity-80">
          <div className="flex items-center gap-2">
            <Brain className="h-4 w-4 text-purple-600 dark:text-purple-400" />
            <span className="text-sm font-medium text-purple-900 dark:text-purple-100">
              {title}
            </span>
          </div>
          <ChevronRight className="h-4 w-4 text-purple-600 dark:text-purple-400 transition-transform group-data-[state=open]:rotate-90" />
        </CollapsibleTrigger>

        <CollapsibleContent className="mt-3 space-y-3 pl-6">
          {thinkingContent && (
            <div>
              <div className="text-xs font-medium text-purple-700 dark:text-purple-300 mb-1">
                思考过程
              </div>
              <div className="prose prose-sm dark:prose-invert max-w-none text-sm">
                <MessageMarkdown
                  showCopyButton={false}
                  content={thinkingContent}
                />
              </div>
            </div>
          )}

          {toolCallGroup && toolCallGroup.toolNames.length > 0 && (
            <div>
              <div className="text-xs font-medium text-blue-700 dark:text-blue-300 mb-1 flex items-center gap-1">
                <Wrench className="h-3 w-3" />
                工具调用
              </div>
              <ul className="text-sm space-y-1">
                {toolCallGroup.toolNames.map((name, i) => (
                  <li key={i} className="text-gray-700 dark:text-gray-300 flex items-center gap-1">
                    <FileText className="h-3 w-3 text-gray-400" />
                    {name}
                  </li>
                ))}
              </ul>
            </div>
          )}
        </CollapsibleContent>
      </Card>
    </Collapsible>
  );
}
```

- [ ] **Step 2: 验证 TS 编译**

Run（workdir: `agentx-frontend-plus`）：`npm run build 2>&1 | Select-Object -Last 20`
Expected: 编译通过（无 error，warn 可接受）

- [ ] **Step 3: 提交**

```bash
git add agentx-frontend-plus/components/agent/ThinkingToolCallGroup.tsx
git commit -m "feat(chat): 新增 ThinkingToolCallGroup 折叠展示组件"
```

---

### Task 4: `chat-panel.tsx` 消息渲染改造

**Files:**
- Modify: `agentx-frontend-plus/components/chat-panel.tsx`

**Interfaces:**
- Consumes: 
  - `MessageType.THINKING_START/PROGRESS/END/TOOL_CALL_GROUP_END`（Task 1 引入）
  - `ToolCallGroup` 类型（Task 3 引入）
  - `AgentChatResponse.payload` 携带工具调用汇总 JSON
- Produces: 流式累积逻辑、消息结构扩展、渲染时插入折叠块

- [ ] **Step 1: 在文件顶部 import 区域增加 import**

定位文件顶部（约 1-50 行），在现有 import 后面追加：

```typescript
import { ThinkingToolCallGroup, type ToolCallGroup } from "@/components/agent/ThinkingToolCallGroup";
```

- [ ] **Step 2: 扩展消息类型**

定位到文件内 `Message` 类型定义（用 `grep "type Message"` 或类似位置查找），扩展为：

```typescript
type Message = {
  id: string;
  role: "USER" | "ASSISTANT" | "SYSTEM" | string;
  content: string;
  type?: MessageType;
  createdAt?: string;
  fileUrls?: string[];
  isStreaming?: boolean;
  // 新增 ↓
  thinkingContent?: string;
  isThinkingComplete?: boolean;
  toolCallGroup?: ToolCallGroup;
};
```

如果已有同名类型，按现有风格合并（保持向后兼容字段）。

- [ ] **Step 3: 在 `handleStreamDataMessage` 中处理新的消息类型**

定位 `handleStreamDataMessage` 函数（约 452 行），在 `const messageType = data.messageType as MessageType || MessageType.TEXT;` 之后、`const currentMessageId = ...` 之前，插入：

```typescript
    // 处理思考链消息（THINKING_START/PROGRESS/END）
    if (messageType === MessageType.THINKING_START ||
        messageType === MessageType.THINKING_PROGRESS ||
        messageType === MessageType.THINKING_END) {
      // 找到或创建当前 assistant 消息
      const targetId = `assistant-thinking-${baseMessageId}-seq${messageSequenceNumber.current}`;
      setMessages(prev => {
        const idx = prev.findIndex(m => m.id === targetId);
        const existing = idx >= 0 ? prev[idx] : {
          id: targetId,
          role: "ASSISTANT",
          content: "",
          type: MessageType.TEXT,
          createdAt: new Date().toISOString(),
        };
        const next = { ...existing };
        if (messageType === MessageType.THINKING_PROGRESS && data.content) {
          next.thinkingContent = (next.thinkingContent || "") + data.content;
        } else if (messageType === MessageType.THINKING_END) {
          next.isThinkingComplete = true;
        }
        if (idx >= 0) {
          const arr = [...prev];
          arr[idx] = next;
          return arr;
        }
        return [...prev, next];
      });
      return;
    }

    // 处理 TOOL_CALL_GROUP_END（payload 携带汇总 JSON）
    if (messageType === MessageType.TOOL_CALL_GROUP_END) {
      try {
        const group: ToolCallGroup = data.payload
          ? JSON.parse(data.payload)
          : { count: 0, fileCount: 0, toolNames: [] };
        const targetId = `assistant-toolgroup-${baseMessageId}-seq${messageSequenceNumber.current}`;
        setMessages(prev => {
          const idx = prev.findIndex(m => m.id === targetId);
          const existing = idx >= 0 ? prev[idx] : {
            id: targetId,
            role: "ASSISTANT",
            content: "",
            type: MessageType.TEXT,
            createdAt: new Date().toISOString(),
          };
          const next = { ...existing, toolCallGroup: group };
          if (idx >= 0) {
            const arr = [...prev];
            arr[idx] = next;
            return arr;
          }
          return [...prev, next];
        });
      } catch (e) {
        // payload 解析失败忽略
        console.warn("TOOL_CALL_GROUP_END payload 解析失败", e);
      }
      return;
    }
```

注意：`MessageType.THINKING_START` 和 `MessageType.TOOL_CALL_GROUP_END` 需要在 `MessageType` 枚举中存在 —— 后端 Task 1 已加入，但前端 `MessageType` 枚举可能未定义这些值。如果缺失，需要在前端类型定义中追加（参考前端 `MessageType` 枚举位置）。

- [ ] **Step 4: 验证前端 MessageType 枚举已包含新值**

Run（workdir: `agentx-frontend-plus`）：`Select-String -Path "types" -Pattern "enum MessageType" -Recurse`
Expected: 找到枚举定义位置

如果枚举中**没有** `THINKING_START/PROGRESS/END/TOOL_CALL_GROUP_END`，在枚举中追加：

```typescript
export enum MessageType {
  TEXT = "TEXT",
  TOOL_CALL = "TOOL_CALL",
  // ... 已有
  THINKING_START = "THINKING_START",
  THINKING_PROGRESS = "THINKING_PROGRESS",
  THINKING_END = "THINKING_END",
  TOOL_CALL_GROUP_END = "TOOL_CALL_GROUP_END",
}
```

（具体看现有枚举的实际拼写风格。）

- [ ] **Step 5: 修改助手消息渲染处，插入 `ThinkingToolCallGroup`**

定位 `messages.map` 渲染块内 AI 消息的 `/* 消息内容 */` 注释（约 750 行 `message.content && ... MessageMarkdown` 之前），在 `message.type` 文本提示块**之后、文件显示之前**，插入：

```tsx
{/* 思考 + 工具调用折叠块（仅 assistant 消息） */}
{(message.thinkingContent || message.toolCallGroup) && (
  <div className="mb-2">
    <ThinkingToolCallGroup
      thinkingContent={message.thinkingContent}
      isThinkingComplete={message.isThinkingComplete}
      toolCallGroup={message.toolCallGroup}
    />
  </div>
)}
```

- [ ] **Step 6: 移除"工具调用"独立块渲染（如果存在）**

定位 `case MessageType.TOOL_CALL:` 渲染块（约 632 行），**删除整个 case 块**或改为不渲染（流式累积的 `THINKING_*` 和 `TOOL_CALL_GROUP_END` 已经处理了工具调用的展示）。

注：保留代码以防误删，可以注释掉：

```tsx
// 已废弃：工具调用现在折叠到 ThinkingToolCallGroup 中展示
// case MessageType.TOOL_CALL: ...
```

- [ ] **Step 7: 编译验证**

Run（workdir: `agentx-frontend-plus`）：`npm run build 2>&1 | Select-Object -Last 20`
Expected: 编译通过

- [ ] **Step 8: 提交**

```bash
git add agentx-frontend-plus/components/chat-panel.tsx
git commit -m "feat(chat): chat-panel 集成 ThinkingToolCallGroup，处理 THINKING_* 和 TOOL_CALL_GROUP_END 流"
```

---

### Task 5: trace 详情页改造

**Files:**
- Modify: `agentx-frontend-plus/app/(main)/traces/agents/[agentId]/sessions/[sessionId]/page.tsx`

**Interfaces:**
- Consumes: trace 详情数据（已有 `TOOL_CALL` 步骤）
- Produces: 折叠展示多条 `TOOL_CALL` 步骤，标题"执行 N 条命令"

- [ ] **Step 1: 找到 trace 详情页中处理 `TOOL_CALL` 步骤的代码**

Run: `Select-String -Path "agentx-frontend-plus/app/(main)/traces" -Pattern "TOOL_CALL" -Recurse`
Expected: 找到页面中处理 TOOL_CALL 的位置

- [ ] **Step 2: 在文件顶部 import 区域增加 import**

在 trace 详情页顶部追加：

```tsx
import { ThinkingToolCallGroup, type ToolCallGroup } from "@/components/agent/ThinkingToolCallGroup";
```

- [ ] **Step 3: 把多个 `TOOL_CALL` 步骤合并为 `ToolCallGroup` 数据**

定位 trace 详情页中遍历 execution details 的代码（约 100-180 行有多个 `case 'TOOL_CALL'`），在合适的渲染位置（assistant 消息块结束前），构造 `ToolCallGroup` 并渲染：

```tsx
{(() => {
  // 从 execution details 收集该 assistant 消息下的所有 TOOL_CALL
  const toolNames: string[] = [];
  for (const detail of details) {
    if (detail.stepType === 'TOOL_CALL' && detail.toolName) {
      toolNames.push(detail.toolName);
    }
  }
  if (toolNames.length === 0) return null;
  const group: ToolCallGroup = {
    count: toolNames.length,
    fileCount: toolNames.filter(n => n.includes('knowledge') || n.includes('file_read')).length,
    toolNames,
  };
  return (
    <div className="mt-2">
      <ThinkingToolCallGroup toolCallGroup={group} />
    </div>
  );
})()}
```

- [ ] **Step 4: 移除原来散开的单条 `TOOL_CALL` 渲染**

定位原 `case 'TOOL_CALL':` 渲染块（约 94、110、135、158、172 行），将原来在主流程中展示 `TOOL_CALL` 步骤的代码**删除或注释**（仅在汇总折叠块中展示）。

- [ ] **Step 5: 编译验证**

Run（workdir: `agentx-frontend-plus`）：`npm run build 2>&1 | Select-Object -Last 20`
Expected: 编译通过

- [ ] **Step 6: 提交**

```bash
git add agentx-frontend-plus/app/\(main\)/traces/agents/\[agentId\]/sessions/\[sessionId\]/page.tsx
git commit -m "feat(trace): trace 详情页工具调用折叠展示"
```

---

## 自我审查

- ✅ Spec 第 1 节（背景与目标）：Task 2 后端 + Task 4 前端实现
- ✅ Spec 第 2 节（设计决策）：所有决策在 Task 4 标题构造 + Task 3 组件 defaultOpen={false} 中落实
- ✅ Spec 第 3 节（后端设计）：Task 1（枚举扩展）+ Task 2（流式改造 + metadata 持久化）
- ✅ Spec 第 4 节（前端设计）：Task 3（折叠组件）+ Task 4（chat-panel 集成）
- ✅ Spec 第 4.3 节（trace 详情页）：Task 5
- ✅ Spec 第 5 节（向后兼容）：旧消息无 metadata → 折叠块不渲染
- ✅ Spec 第 6 节（测试要点）：每个 Task 的"编译验证"步骤覆盖
- ✅ Spec 第 7 节（不在范围）：已显式排除 rag-chat、agent-preview

类型一致性：
- `MessageType.THINKING_START/PROGRESS/END/TOOL_CALL_GROUP_END`：Task 1 定义 → Task 2 引用 → Task 4 前端使用
- `ToolCallGroup` 接口：Task 3 定义（`{ count, fileCount, toolNames }`）→ Task 4、Task 5 引用
- `Message.thinkingContent/isThinkingComplete/toolCallGroup`：Task 4 扩展 → Task 4 自身渲染使用

无 placeholder / TBD / TODO。

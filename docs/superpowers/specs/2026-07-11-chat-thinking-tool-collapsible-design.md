# 普通 Chat 模式工具调用 & 思考过程折叠展示

**日期**: 2026-07-11
**状态**: 设计待用户最终确认
**范围**: `components/chat-panel.tsx`（普通 chat 面板）+ `app/(main)/traces/agents/[agentId]/sessions/[sessionId]/page.tsx`（trace 详情页）

> 注：RAG 模式 (`components/rag-chat/`) 已有 ThinkingProcess 折叠组件，本设计不涉及。
> 注：`components/agent-preview-chat.tsx` 暂不在本次范围。

## 1. 背景与目标

**当前问题**：
- 普通 chat 流（`AbstractMessageHandler.processStreamingChat`）没有启用 `onPartialReasoning`，思维链内容以 `` 标签嵌入 content 推到前端直接展示成原文，视觉混乱。
- 每个工具调用发一条独立 `TOOL_CALL` 消息，前端 `chat-panel.tsx` 渲染成 N 个独立块，无折叠/汇总。

**目标**：参考 Claude/Gemini 等主流产品，呈现"思考 N 次，查看 M 个文件 >"的可折叠块结构，默认折叠，点击展开查看思考详情和工具调用列表。

## 2. 设计决策（已与用户确认）

| 决策点 | 选择 | 原因 |
|---|---|---|
| 改造路线 | 前后端一起改 | 流式体验顺、消息能持久化分析 |
| 分组粒度 | 一个 assistant 消息一个折叠块 | 边界清晰、UI 干净 |
| 默认状态 | 折叠收起 | 页面干净，主流做法 |
| 标题文案 | "思考 N 次，查看 M 个文件" | 简洁、有信息量、与图二一致 |

## 3. 后端设计

### 3.1 新增 `MessageType` 枚举值

```java
public enum MessageType {
    TEXT, TOOL_CALL, TASK_EXEC, TASK_STATUS_TO_LOADING, TASK_STATUS_TO_FINISH,
    TASK_SPLIT_FINISH,
    RAG_RETRIEVAL_START, RAG_RETRIEVAL_PROGRESS, RAG_RETRIEVAL_END,
    RAG_THINKING_START, RAG_THINKING_PROGRESS, RAG_THINKING_END,
    RAG_ANSWER_START, RAG_ANSWER_PROGRESS, RAG_ANSWER_END,
    // 新增 ↓
    THINKING_START,            // 普通 chat 思考开始
    THINKING_PROGRESS,         // 普通 chat 思考增量
    THINKING_END,              // 普通 chat 思考结束
    TOOL_CALL_GROUP_END        // 工具调用组结束（带汇总：count、fileCount、toolNames）
}
```

### 3.2 `AbstractMessageHandler.processStreamingChat` 改造

参考 RAG 的实现（`RagMessageHandler.java:200-246`）：

```java
// 思维链状态
final boolean[] thinkingStarted = {false};
final boolean[] thinkingEnded = {false};
final boolean[] hasThinkingProcess = {false};

// 工具调用累积
final AtomicInteger toolCallCount = new AtomicInteger(0);
final AtomicInteger fileViewCount = new AtomicInteger(0); // 依据工具名/参数判断
final List<String> toolNames = Collections.synchronizedList(new ArrayList<>());

tokenStream.onPartialReasoning(reasoning -> {
    hasThinkingProcess[0] = true;
    if (!thinkingStarted[0]) {
        transport.sendMessage(connection,
            AgentChatResponse.build("开始思考...", MessageType.THINKING_START));
        thinkingStarted[0] = true;
    }
    transport.sendMessage(connection,
        AgentChatResponse.build(reasoning, MessageType.THINKING_PROGRESS));
});

tokenStream.onPartialResponse(reply -> {
    if (hasThinkingProcess[0] && !thinkingEnded[0]) {
        transport.sendMessage(connection,
            AgentChatResponse.build("思考完成", MessageType.THINKING_END));
        thinkingEnded[0] = true;
    }
    if (!hasThinkingProcess[0] && !thinkingStarted[0]) {
        transport.sendMessage(connection,
            AgentChatResponse.build("开始思考...", MessageType.THINKING_START));
        transport.sendMessage(connection,
            AgentChatResponse.build("思考完成", MessageType.THINKING_END));
        thinkingStarted[0] = true;
        thinkingEnded[0] = true;
    }
    // 累加文本到 llmEntity
    // ... 原有逻辑
});

// onToolExecuted 中累加
tokenStream.onToolExecuted(toolExecution -> {
    toolCallCount.incrementAndGet();
    String name = toolExecution.request().name();
    toolNames.add(name);
    // 判断"查看文件"语义：知识库检索/读文件类工具
    if (isFileViewTool(name)) {
        fileViewCount.incrementAndGet();
    }
    // 原有持久化逻辑保留
});

// onCompleteResponse 之前发送汇总
tokenStream.onCompleteResponse(chatResponse -> {
    if (toolCallCount.get() > 0) {
        // 用 metadata JSON 发汇总
        String meta = objectMapper.writeValueAsString(Map.of(
            "count", toolCallCount.get(),
            "fileCount", fileViewCount.get(),
            "toolNames", toolNames
        ));
        transport.sendMessage(connection,
            AgentChatResponse.buildEndMessageWithMeta(meta, MessageType.TOOL_CALL_GROUP_END));
    }
    // 原有逻辑
});
```

### 3.3 持久化

`MessageEntity.metadata` 字段已存在（`String` 类型），存 JSON：

```json
{
  "thinkingContent": "用户问知识库...",
  "toolCallGroup": {
    "count": 2,
    "fileCount": 2,
    "toolNames": ["knowledge_search", "knowledge_search"]
  }
}
```

`saveMessageAndUpdateContext` 中，保存 assistant 消息时把 `metadata` 写入 `MessageEntity.metadata`。

### 3.4 错误/中断处理

- 流被打断（`USER_INTERRUPTED`）时，onError 回调里补发 `THINKING_END` 和 `TOOL_CALL_GROUP_END`（带已累计的 count）
- 数据库连接异常等其他错误，至少保证 `TOOL_CALL_GROUP_END` 已发

## 4. 前端设计

### 4.1 新组件 `components/agent/ThinkingToolCallGroup.tsx`

参考 `components/rag-chat/ThinkingProcess.tsx` 风格，使用 `Collapsible` + `CollapsibleTrigger` + `CollapsibleContent`：

```tsx
"use client";
import { Brain, ChevronDown, ChevronRight, Wrench, FileText } from "lucide-react";
import { Card } from "@/components/ui/card";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { MessageMarkdown } from "@/components/ui/message-markdown";

interface ThinkingToolCallGroupProps {
  thinkingContent?: string;
  isThinkingComplete?: boolean;
  toolCallGroup?: {
    count: number;
    fileCount: number;
    toolNames: string[];
  };
  defaultExpanded?: boolean;  // 默认 false
}

export function ThinkingToolCallGroup({
  thinkingContent,
  isThinkingComplete,
  toolCallGroup,
  defaultExpanded = false,
}: ThinkingToolCallGroupProps) {
  if (!thinkingContent && !toolCallGroup) return null;

  const titleParts: string[] = [];
  if (thinkingContent) titleParts.push(`思考 ${isThinkingComplete ? '已完成' : '中'}`);
  if (toolCallGroup) {
    if (toolCallGroup.fileCount > 0) {
      titleParts.push(`查看 ${toolCallGroup.fileCount} 个文件`);
    } else if (toolCallGroup.count > 0) {
      titleParts.push(`调用 ${toolCallGroup.count} 个工具`);
    }
  }
  const title = titleParts.join("，") || "处理过程";

  return (
    <Collapsible defaultOpen={defaultExpanded}>
      <Card className="px-4 py-2 bg-purple-50 dark:bg-purple-950/20 mb-2">
        <CollapsibleTrigger className="flex items-center justify-between w-full text-left">
          <div className="flex items-center gap-2">
            <Brain className="h-4 w-4 text-purple-600" />
            <span className="text-sm font-medium">{title}</span>
          </div>
          <ChevronRight className="h-4 w-4 text-muted-foreground transition-transform group-data-[state=open]:rotate-90" />
        </CollapsibleTrigger>
        <CollapsibleContent className="mt-3 space-y-3 pl-6">
          {thinkingContent && (
            <div className="prose prose-sm max-w-none text-sm">
              <div className="text-xs font-medium text-purple-700 mb-1">思考过程</div>
              <MessageMarkdown content={thinkingContent} showCopyButton={false} />
            </div>
          )}
          {toolCallGroup && toolCallGroup.toolNames.length > 0 && (
            <div>
              <div className="text-xs font-medium text-blue-700 mb-1 flex items-center gap-1">
                <Wrench className="h-3 w-3" />
                工具调用
              </div>
              <ul className="text-sm space-y-1">
                {toolCallGroup.toolNames.map((name, i) => (
                  <li key={i} className="text-gray-700">· {name}</li>
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

### 4.2 `chat-panel.tsx` 改造

**消息数据结构扩展**：
```ts
type ChatMessage = {
  ...,
  thinkingContent?: string;
  isThinkingComplete?: boolean;
  toolCallGroup?: { count: number; fileCount: number; toolNames: string[] };
}
```

**流式累积**（在 `handleStreamDataMessage` 中）：
- `THINKING_START/PROGRESS` → 累积到当前 assistant 消息的 `thinkingContent`
- `THINKING_END` → 标记 `isThinkingComplete = true`
- `TOOL_CALL` → 计入临时计数器（不立即渲染，最终由 `TOOL_CALL_GROUP_END` 一次性更新）
- `TOOL_CALL_GROUP_END` → 把 metadata 解析为 `toolCallGroup` 写入消息

**渲染**（在 `messages.map` 块）：
- 在 assistant 消息头部（content 之前）插入 `<ThinkingToolCallGroup>` 组件
- 移除原来的"工具调用"独立块渲染

### 4.3 Trace 详情页改造

`app/(main)/traces/agents/[agentId]/sessions/[sessionId]/page.tsx`：
- 把当前 `case 'TOOL_CALL'` 的多条独立块改成"执行 N 条命令"折叠块
- thinking 字段（如果存在）合并到同一个折叠块

## 5. 向后兼容

- **历史消息**：若 `metadata` 为空或不包含新字段，折叠块不渲染，原文本照常展示
- **数据库迁移**：本次不涉及新字段（`metadata` 已有），不需要 schema 变更
- **V20250812001 迁移脚本**：仅修复历史 `created_at IS NULL` 数据，与本设计独立

## 6. 测试要点

- 普通 chat 单工具调用：折叠块标题"调用 1 个工具"
- 普通 chat 多工具调用 + 思维链：折叠块标题"思考已完成，查看 N 个文件"
- 无工具调用无思考：折叠块不渲染
- 流式中断：折叠块仍渲染（可能标题"思考中"）
- 历史消息回放：旧消息正常展示，无折叠块

## 7. 不在本次范围

- `components/agent-preview-chat.tsx`（用户截图未涉及，明确排除）
- `components/rag-chat/*`（已有折叠能力）
- 数据库 schema 变更
- 思考内容与回答内容分离的进一步细化（如分段展示）

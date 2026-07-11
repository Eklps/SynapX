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
                  <li
                    key={i}
                    className="text-gray-700 dark:text-gray-300 flex items-center gap-1"
                  >
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

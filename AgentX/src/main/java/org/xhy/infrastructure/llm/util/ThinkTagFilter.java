package org.xhy.infrastructure.llm.util;

/**
 * 流式 <think> 标签剥离器（LangChain4j fork 兼容性工具）
 *
 * <p>
 * langchain4j 1.0.4.3-beta7 (com.github.lucky-aeon fork) 不会自动剥离 LLM
 * 输出里的 {@code <think>...</think>} 块——它们会作为普通文本片段
 * 通过 onPartialResponse / onCompleteResponse 流到调用方。本工具把过滤逻辑
 * 抽出来供后端 Java 代码使用，与前端的 lib/think-tag-filter.ts 语义保持一致。
 * </p>
 *
 * <p>规则：</p>
 * <ul>
 * <li>见到 {@code <think>} → 进入隐藏模式，丢弃其后续内容</li>
 * <li>隐藏模式中见到 {@code </think>} → 退出隐藏模式，恢复正常输出</li>
 * <li>未配对的 {@code <think>}（流被截断）→ 当前 chunk 内剩余全部丢弃</li>
 * </ul>
 *
 * <p>状态机跨 chunk 必须持续，不能每 chunk 新建。典型用法：</p>
 * <pre>{@code
 * ThinkTagFilter.State state = ThinkTagFilter.State.INITIAL;
 * String visible = state.filter(chunk);
 * }</pre>
 */
public final class ThinkTagFilter {

    private ThinkTagFilter() {
    }

    /** 跨 chunk 持久化的状态机 */
    public static final class State {
        private boolean inThinkTag;

        public static final State INITIAL = new State();

        /** 处理单个 chunk，返回可见部分。inThinkTag 跨调用持续。 */
        public String filter(String chunk) {
            if (chunk == null || chunk.isEmpty()) {
                return "";
            }
            StringBuilder result = new StringBuilder();
            int i = 0;
            int n = chunk.length();
            while (i < n) {
                if (inThinkTag) {
                    int endIdx = chunk.indexOf("</think>", i);
                    if (endIdx == -1) {
                        // 未闭合，剩余全部丢弃
                        return result.toString();
                    }
                    i = endIdx + "</think>".length();
                    inThinkTag = false;
                } else {
                    int startIdx = chunk.indexOf("<think>", i);
                    if (startIdx == -1) {
                        result.append(chunk, i, n);
                        return result.toString();
                    }
                    result.append(chunk, i, startIdx);
                    i = startIdx + "<think>".length();
                    inThinkTag = true;
                }
            }
            return result.toString();
        }

        public boolean isInThinkTag() {
            return inThinkTag;
        }

        public void reset() {
            inThinkTag = false;
        }
    }
}
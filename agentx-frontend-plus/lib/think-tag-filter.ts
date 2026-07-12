/**
 * 流式 <think> 标签过滤器（LangChain4j fork 兼容性兜底）
 *
 * <p>langchain4j 1.0.4.3-beta7 (com.github.lucky-aeon fork) 不会自动剥离 LLM
 * 输出里的 <think>...</think> 文本块——这部分会作为 TEXT 消息内容的一部分
 * 原样下发给前端。前端需要在拼接/渲染前把 <think>...</p>
 * <p>...</p>
 * <p><think> 内容塞进流里的场景。规则：</p>
 * <ul>
 * <li>见到 `<think>` → 进入隐藏模式，丢弃其后续内容</li>
 * <li>在隐藏模式中见到 `</think>` → 退出隐藏模式，恢复正常输出</li>
 * <li>隐藏模式中见到新的 `<think>`（嵌套）→ 仍视为隐藏，不变更状态</li>
 * <li>未配对的 `<think>`（流被截断）→ 当前 chunk 里剩余内容全部丢弃</li>
 * </ul>
 *
 * <p>状态机实例必须跨 chunk 持续存在（用 useRef 或闭包），不能每 chunk 新建，
 * 否则遇到跨边界标签就会失效。</p>
 */

export interface ThinkFilterState {
  /** 是否处于隐藏模式（<think> 与 </think> 之间） */
  inThinkTag: boolean;
}

/** 初始状态：不在隐藏模式 */
export const INITIAL_THINK_FILTER_STATE: ThinkFilterState = {
  inThinkTag: false,
};

/**
 * 把单个 chunk（流式到达的一段文本）经过 <think> 过滤，吐出"应展示给用户的内容"。
 *
 * @param chunk 新到达的文本片段（可能包含部分标签）
 * @param state 跨 chunk 持久化的状态机（调用方负责存）
 * @returns 过滤后的展示文本（无 <think> 标签/内容）
 */
export function filterThinkTag(chunk: string, state: ThinkFilterState): string {
  if (!chunk) return "";

  let result = "";
  let i = 0;

  while (i < chunk.length) {
    if (state.inThinkTag) {
      // 隐藏模式：找 </think>
      const endIdx = chunk.indexOf("</think>", i);
      if (endIdx === -1) {
        // 未找到结束标签：剩余全部丢弃
        return result;
      }
      // 跳过 </think>，进入正常模式
      i = endIdx + "</think>".length;
      state.inThinkTag = false;
    } else {
      // 正常模式：找 <think>
      const startIdx = chunk.indexOf("<think>", i);
      if (startIdx === -1) {
        // 没找到：剩余全部为可见内容
        result += chunk.substring(i);
        return result;
      }
      // 把 <think> 之前的可见内容拼上
      result += chunk.substring(i, startIdx);
      // 进入隐藏模式
      i = startIdx + "<think>".length;
      state.inThinkTag = true;
    }
  }
  return result;
}
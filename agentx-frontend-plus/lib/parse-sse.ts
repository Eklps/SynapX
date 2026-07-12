/**
 * SSE 解析器（纯函数）
 *
 * <p>修复了经典 bug：服务端在最后一条事件后只发 `\n` 或直接 close 流，
 * 原先的 `split("\n\n") + pop()` 模式会把整段当作"残留"丢掉，导致
 * 用户看到回复最后几个字缺失（刷新后服务端完整入库才能看到）。</p>
 *
 * <p>策略：每次 chunk 到达时按 `\n\n` 切分，对切分出来的**完整事件**
 * 立即调用 onMessage；最后一段当作"残留"放进 buffer 等下一个 chunk。
 * 当 chunk 是最后一段（done=true）时，把残留 buffer 当作最后一条事件
 * 强制 flush 一次。</p>
 *
 * <p>注意：buffer 的累加逻辑是纯函数，调用方负责把 fetch reader 的
 * Uint8Array 通过 TextDecoder 转为 string 再调用本函数；本函数只做
 * "chunk 字符串 → onMessage 回调"。</p>
 */

/**
 * 处理一个 SSE chunk，返回新的 buffer 状态。
 *
 * @param currentBuffer 上一次遗留的未完整事件
 * @param chunk 新到的字符串（已 UTF-8 解码）
 * @param isFinal 是否是流的最后一段（fetch reader 的 done=true）
 * @param onMessage 解析到一个完整事件时回调
 * @returns 处理完后应该作为下一次的 buffer（即"残留"）
 */
export function processSSEChunk(
  currentBuffer: string,
  chunk: string,
  isFinal: boolean,
  onMessage: (jsonData: unknown) => void,
): string {
  const combined = currentBuffer + chunk;

  // 按 SSE 协议切分。SSE 用 \n\n 分隔事件，但实现常会多塞 \r 或单 \n，
  // 这里用正则一次性吃掉任意 \r?\n\r?\n 序列。
  const eventDelimiter = /\r?\n\r?\n/;
  const parts = combined.split(eventDelimiter);

  if (isFinal) {
    // 最后一段：所有切分出来的元素都当作完整事件（包括末尾那个"残留"）
    for (const part of parts) {
      const trimmed = part.trim();
      if (trimmed.startsWith("data:")) {
        const jsonStr = trimmed.startsWith("data:data:")
          ? trimmed.slice(10) // 容错：偶发的双重 data: 前缀
          : trimmed.slice(5).trim();
        try {
          onMessage(JSON.parse(jsonStr));
        } catch {
          // 解析失败：忽略单条坏消息，不阻塞后续事件
        }
      }
    }
    return "";
  }

  // 非最后一段：保留最后一个元素当作下次可能拼完整的"残留"
  const leftover = parts.pop() ?? "";
  for (const part of parts) {
    const trimmed = part.trim();
    if (trimmed.startsWith("data:")) {
      const jsonStr = trimmed.startsWith("data:data:")
        ? trimmed.slice(10)
        : trimmed.slice(5).trim();
      try {
        onMessage(JSON.parse(jsonStr));
      } catch {
        // ignore malformed event
      }
    }
  }
  return leftover;
}
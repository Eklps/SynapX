// 消息内容相关的纯函数工具

/**
 * 判断文本内容是否"看起来像错误消息"。
 *
 * <p>
 * 严格规则：只匹配"以错误关键词开头"的情况，避免正文中"我无法访问互联网"、
 * "抱歉打扰一下"等正常表述被误判为错误。调用方仍可显式传 isError={true} 覆盖。
 * </p>
 *
 * <p>
 * 注意：此函数仅判断"用户向 LLM 提问内容"中的错误指示词。
 * 对后端响应（PSQLException / "Error updating database" 等）的检测
 * 由各组件自己的 `data.content.includes(...)` 处理（本函数不覆盖）。
 * </p>
 *
 * @param content 消息文本
 * @returns 是否以错误关键词起始
 */
export function isErrorMessage(content: string | null | undefined): boolean {
  if (!content) return false;
  const trimmed = content.trim();
  const errorPrefixPatterns = [
    /^错误[：:]/,
    /^失败[：:]/,
    /^处理失败/,
    /^出现了错误/,
    /^配置错误/,
    /^连接失败/,
    /^未找到/,
    /^未配置[：:]/,
    /^抱歉[，,]/,
    /^预览出错[：:]/,
    /^Error[：:]/i,
    /^Failed[：:]/i,
    /^\[ERROR\]/i,
  ];
  return errorPrefixPatterns.some((p) => p.test(trimmed));
}
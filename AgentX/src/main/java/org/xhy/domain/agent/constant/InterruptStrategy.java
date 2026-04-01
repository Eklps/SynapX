package org.xhy.domain.agent.constant;

/** Agent 中断策略枚举
 *
 * 定义当用户手动中止对话时，后台生成线程的处理策略：
 * - COMPLETE：异步跑完，保障记忆连贯性（默认）
 * - IMMEDIATE：立即短路，节省 Token 和线程资源 */
public enum InterruptStrategy {

    /** 保障连贯性模式：即使用户主动中断，后台仍会完成本轮生成并持久化完整内容。
     * 适用场景：写作类、深度分析类 Agent，记忆完整性要求高。 */
    COMPLETE,

    /** 极致省资源模式：检测到中断信号后立即停止生成，将已生成的部分内容落盘。
     * 适用场景：查询类、RAG 问答类 Agent，对记忆断片容忍度高。 */
    IMMEDIATE
}

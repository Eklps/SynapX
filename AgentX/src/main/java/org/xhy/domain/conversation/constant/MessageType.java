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
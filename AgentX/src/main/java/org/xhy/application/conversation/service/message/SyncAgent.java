package org.xhy.application.conversation.service.message;

/**
 * 同步Agent接口，用于非流式对话场景。
 * <p>
 * 与 {@link Agent}（返回 {@link dev.langchain4j.service.TokenStream} 的流式接口）对应，
 * 本接口返回纯文本，适用于通过 {@link dev.langchain4j.service.AiServices} + {@link dev.langchain4j.model.chat.ChatModel}
 * 构建的同步对话，这样工具提供者（{@link dev.langchain4j.service.tool.ToolProvider}）才能在同步路径下生效。
 */
public interface SyncAgent {

    /** 同步对话
     *
     * @param message 用户消息
     * @return 模型回复文本（工具已在内部执行循环中调用） */
    String chat(String message);
}

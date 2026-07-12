package org.xhy.infrastructure.llm.util;

import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xhy.application.conversation.service.handler.context.ChatContext;
import org.xhy.domain.conversation.model.MessageEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 多模态 UserMessage 工厂
 *
 * <p>
 * 负责把 {@link ChatContext}（当前用户消息 + fileUrls）和历史 {@link MessageEntity} 转换为 langchain4j 的 {@link UserMessage}，支持多模态（text
 * + image_url）混合。
 * </p>
 *
 * <p>
 * 设计原则：
 * </p>
 * <ul>
 * <li>无 fileUrls → 直接返回纯文本 UserMessage（兼容现有非多模态消息）</li>
 * <li>有 fileUrls → 把图片读为 base64 data URL，组装成多 content UserMessage</li>
 * <li>图片转换全部失败 → 降级为纯文本，不抛异常</li>
 * </ul>
 */
@Component
public class MultimodalMessageFactory {

    private static final Logger log = LoggerFactory.getLogger(MultimodalMessageFactory.class);

    private final ImageBase64Loader imageBase64Loader;

    public MultimodalMessageFactory(ImageBase64Loader imageBase64Loader) {
        this.imageBase64Loader = imageBase64Loader;
    }

    /** 当前用户消息 → UserMessage（含 text + image_url 块）
     *
     * @param chatContext 聊天上下文
     * @return 多模态或纯文本 UserMessage */
    public UserMessage buildUserMessage(ChatContext chatContext) {
        String text = chatContext == null || chatContext.getUserMessage() == null ? "" : chatContext.getUserMessage();
        List<String> fileUrls = chatContext == null || chatContext.getFileUrls() == null
                ? Collections.emptyList()
                : chatContext.getFileUrls();

        log.info("MultimodalMessageFactory.buildUserMessage ENTER: textLen={}, fileUrls={}", text.length(),
                fileUrls);

        if (fileUrls.isEmpty()) {
            log.info("MultimodalMessageFactory.buildUserMessage: 纯文本分支（fileUrls 为空）");
            return new UserMessage(text);
        }
        return assemble(text, fileUrls);
    }

    /** 把当前轮上传的图片以 image-only {@link UserMessage} 注入 {@link ChatMemory}。
     *
     * <p>
     * 本项目使用的 langchain4j 版本，{@code AiServices} 的方法入参若为 {@link UserMessage}，会被
     * {@code InternalReflectionVariableResolver.asString()} 走 {@code toString()} 序列化成一段文本（而非按多模态内容下发），
     * 因此当前轮图片<b>不能</b>通过 {@code agent.chat(UserMessage)} 传递。这里改为直接写入 memory（与历史消息
     * {@link #buildUserMessageFromHistory} 的处理方式一致），文本仍由 {@code agent.chat(String)} 追加，最终形成
     * {@code [.., image(s), text]} 的消息序列。
     * </p>
     *
     * @param memory 当前会话的聊天内存
     * @param fileUrls 当前轮图片 URL 列表（可空） */
    public void addCurrentTurnImages(ChatMemory memory, List<String> fileUrls) {
        if (memory == null || fileUrls == null || fileUrls.isEmpty()) {
            return;
        }
        for (String url : fileUrls) {
            String dataUrl = imageBase64Loader.toDataUrl(url);
            if (dataUrl == null) {
                log.info("当前轮图片转换 base64 失败，跳过: {}", url);
                continue;
            }
            memory.add(UserMessage.from(ImageContent.from(dataUrl)));
        }
    }

    /** 历史消息（{@link MessageEntity}，可能含 fileUrls）→ UserMessage
     *
     * <p>
     * 用于 {@code MessageWindowChatMemory} 回放历史用户消息，把当时附带的图片也带上。
     * </p>
     *
     * @param messageEntity 历史消息实体
     * @return 多模态或纯文本 UserMessage */
    public UserMessage buildUserMessageFromHistory(MessageEntity messageEntity) {
        if (messageEntity == null) {
            return new UserMessage("");
        }
        String text = messageEntity.getContent() == null ? "" : messageEntity.getContent();
        List<String> fileUrls = messageEntity.getFileUrls() == null
                ? Collections.emptyList()
                : messageEntity.getFileUrls();
        if (fileUrls.isEmpty()) {
            return new UserMessage(text);
        }
        return assemble(text, fileUrls);
    }

    /** 拼装多模态 UserMessage 主体逻辑
     *
     * @param text 文本内容（可空）
     * @param fileUrls 图片 URL 列表
     * @return UserMessage */
    private UserMessage assemble(String text, List<String> fileUrls) {
        List<Content> parts = new ArrayList<>();
        if (text != null && !text.isEmpty()) {
            parts.add(TextContent.from(text));
        }

        boolean hasImage = false;
        for (String url : fileUrls) {
            String dataUrl = imageBase64Loader.toDataUrl(url);
            log.info("MultimodalMessageFactory: fileUrl={} -> dataUrl={}", url,
                    dataUrl == null ? "NULL" : "OK[len=" + dataUrl.length() + ",prefix="
                            + dataUrl.substring(0, Math.min(40, dataUrl.length())) + "]");
            if (dataUrl == null) {
                continue;
            }
            parts.add(ImageContent.from(dataUrl));
            hasImage = true;
        }

        // 全部图片转换失败 → 退回纯文本
        if (!hasImage) {
            log.info("所有图片转换 base64 失败，降级为纯文本消息, fileUrls.size={}", fileUrls.size());
            return new UserMessage(text == null ? "" : text);
        }

        log.info("MultimodalMessageFactory: 拼装 UserMessage 成功, textLen={}, imageCount={}", text == null ? 0
                : text.length(), (int) parts.stream().filter(p -> p instanceof ImageContent).count());
        return UserMessage.from(parts);
    }
}

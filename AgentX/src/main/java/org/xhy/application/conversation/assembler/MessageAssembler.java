package org.xhy.application.conversation.assembler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.xhy.domain.conversation.model.MessageEntity;
import org.xhy.application.conversation.dto.MessageDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 消息对象转换器 */
public class MessageAssembler {

    private static final Logger log = LoggerFactory.getLogger(MessageAssembler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    /** 将Message实体转换为MessageDTO
     *
     * <p>metadata 字段是后端落库时的 JSON 字符串（结构由 AbstractMessageHandler 写入），
     * 这里解析出 thinkingContent / toolCallGroup 平铺到 DTO 上方便前端直接消费。</p>
     *
     * @param message 消息实体
     * @return 消息DTO */
    public static MessageDTO toDTO(MessageEntity message) {
        if (message == null) {
            return null;
        }

        MessageDTO dto = new MessageDTO();
        BeanUtils.copyProperties(message, dto);

        // 解析 metadata JSON：剥离 think 标签已经在 AbstractMessageHandler 落库时完成，
        // 这里只把已存的 thinkingContent / toolCallGroup 平铺出来
        String meta = message.getMetadata();
        if (meta != null && !meta.isEmpty()) {
            dto.setMetadata(meta);
            try {
                Map<String, Object> parsed = MAPPER.readValue(meta, MAP_TYPE);
                Object tc = parsed.get("thinkingContent");
                if (tc instanceof String s && !s.isEmpty()) {
                    dto.setThinkingContent(s);
                }
                Object tcg = parsed.get("toolCallGroup");
                if (tcg != null) {
                    dto.setToolCallGroup(tcg);
                }
            } catch (Exception e) {
                // 解析失败不影响主流程，保留原始 metadata 字符串供调试
                log.warn("消息 metadata 解析失败，messageId={}", message.getId(), e);
            }
        }
        return dto;
    }

    /** 将消息实体列表转换为DTO列表
     *
     * @param messages 消息实体列表
     * @return 消息DTO列表 */
    public static List<MessageDTO> toDTOs(List<MessageEntity> messages) {
        if (messages == null) {
            return Collections.emptyList();
        }

        return messages.stream().map(MessageAssembler::toDTO).collect(Collectors.toList());
    }
}
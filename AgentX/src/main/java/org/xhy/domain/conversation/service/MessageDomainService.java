package org.xhy.domain.conversation.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.stereotype.Service;
import org.xhy.domain.conversation.model.ContextEntity;
import org.xhy.domain.conversation.model.MessageEntity;
import org.xhy.domain.conversation.repository.ContextRepository;
import org.xhy.domain.conversation.repository.MessageRepository;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageDomainService {

    private final MessageRepository messageRepository;

    private final ContextRepository contextRepository;

    public MessageDomainService(MessageRepository messageRepository, ContextRepository contextRepository) {
        this.messageRepository = messageRepository;
        this.contextRepository = contextRepository;
    }

    public List<MessageEntity> listByIds(List<String> ids) {
        return messageRepository.selectByIds(ids);
    }

    /** 保存消息并且更新消息到上下文 */
    public void saveMessageAndUpdateContext(List<MessageEntity> messageEntities, ContextEntity contextEntity) {
        if (messageEntities == null || messageEntities.isEmpty()) {
            return;
        }
        // 收集插入后的 id（MyBatis 单条 insert 会在 insert 后回填 id）
        java.util.List<String> insertedIds = new java.util.ArrayList<>();
        for (MessageEntity messageEntity : messageEntities) {
            messageEntity.setId(null);
            messageEntity.setCreatedAt(LocalDateTime.now());
            // 逐条 insert 避免 MyBatis-Plus 批量 insert 内部 sort by 主键 NPE
            messageRepository.insert(messageEntity);
            insertedIds.add(messageEntity.getId());
        }
        contextEntity.getActiveMessages().addAll(insertedIds);
        contextRepository.insertOrUpdate(contextEntity);
    }

    /** 保存消息
     *
     * <p>
     * 注意：这里用 for 循环逐条 insert 替代 MyBatis-Plus 的批量 insert，是为了避免底层
     * {@code BaseMapper.insert(Collection)} 在排序（按主键）时遇到 null key 抛 NPE。 MyBatis-Plus 的批量 insert 在
     * 3.5.x 中会先把 list 排序再分组，遇到某些 entity 主键或排序键为 null 时会 NPE。 这里数据量小，性能差异可忽略。
     * </p> */
    public void saveMessage(List<MessageEntity> messageEntities) {
        if (messageEntities == null || messageEntities.isEmpty()) {
            return;
        }
        for (MessageEntity entity : messageEntities) {
            messageRepository.insert(entity);
        }
    }

    public void updateMessage(MessageEntity message) {
        messageRepository.updateById(message);
    }

    public boolean isFirstConversation(String sessionId) {
        // 阈值 ==1：只在第一轮触发智能重命名。原 <=3 会导致前 3 轮每次都覆盖标题
        return messageRepository
                .selectCount(Wrappers.<MessageEntity>lambdaQuery().eq(MessageEntity::getSessionId, sessionId)) == 1;
    }
}

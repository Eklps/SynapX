package org.xhy.application.conversation.dto;

import org.xhy.domain.conversation.constant.MessageType;
import org.xhy.domain.conversation.constant.Role;
import org.xhy.domain.conversation.model.MessageEntity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 消息DTO，用于API响应 */
public class MessageDTO {
    /** 消息ID */
    private String id;
    /** 消息角色 */
    private Role role;
    /** 消息内容 */
    private String content;
    /** 创建时间 */
    private LocalDateTime createdAt;
    /** 提供商 */
    private String provider;
    /** 模型 */
    private String model;

    /** 消息类型 */
    private MessageType messageType;

    private List<String> fileUrls = new ArrayList<>();

    /** 原始 metadata JSON 字符串（保留供调试/未解析场景） */
    private String metadata;
    /** 思考链内容（解析自 metadata.thinkingContent） */
    private String thinkingContent;
    /** 工具调用汇总（解析自 metadata.toolCallGroup），结构由前端 ToolCallGroup 类型定义 */
    private Object toolCallGroup;

    /** 无参构造函数 */
    public MessageDTO() {
    }

    // Getter和Setter方法
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }

    public List<String> getFileUrls() {
        return fileUrls;
    }

    public void setFileUrls(List<String> fileUrls) {
        this.fileUrls = fileUrls;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }

    public String getThinkingContent() {
        return thinkingContent;
    }

    public void setThinkingContent(String thinkingContent) {
        this.thinkingContent = thinkingContent;
    }

    public Object getToolCallGroup() {
        return toolCallGroup;
    }

    public void setToolCallGroup(Object toolCallGroup) {
        this.toolCallGroup = toolCallGroup;
    }
}
package org.xhy.domain.tool.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.xhy.domain.tool.model.UserToolEntity;
import org.xhy.domain.tool.repository.UserToolRepository;
import org.xhy.interfaces.dto.tool.request.QueryToolRequest;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 用户已安装工具 service */
@Service
public class UserToolDomainService {

    private static final Logger log = LoggerFactory.getLogger(UserToolDomainService.class);

    private final UserToolRepository userToolRepository;

    public UserToolDomainService(UserToolRepository userToolRepository) {
        this.userToolRepository = userToolRepository;
    }

    public void add(UserToolEntity userToolEntity) {
        userToolRepository.checkInsert(userToolEntity);
    }

    public Page<UserToolEntity> listByUserId(String userId, QueryToolRequest queryToolRequest) {
        LambdaQueryWrapper<UserToolEntity> wrapper = Wrappers.<UserToolEntity>lambdaQuery()
                .eq(UserToolEntity::getUserId, userId);
        return userToolRepository.selectPage(new Page<>(queryToolRequest.getPage(), queryToolRequest.getPageSize()),
                wrapper);
    }

    public UserToolEntity findByToolIdAndUserId(String toolId, String userId) {
        LambdaQueryWrapper<UserToolEntity> wrapper = Wrappers.<UserToolEntity>lambdaQuery()
                .eq(UserToolEntity::getToolId, toolId).eq(UserToolEntity::getUserId, userId);
        return userToolRepository.selectOne(wrapper);
    }

    public void update(UserToolEntity userToolEntity) {

        userToolRepository.checkedUpdateById(userToolEntity);
    }

    public void delete(String toolId, String userId) {
        LambdaQueryWrapper<UserToolEntity> wrapper = Wrappers.<UserToolEntity>lambdaQuery()
                .eq(UserToolEntity::getToolId, toolId).eq(UserToolEntity::getUserId, userId);
        userToolRepository.checkedDelete(wrapper);
    }

    // 获取工具的安装次数
    public Map<String, Long> getToolsInstall(List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return new HashMap<>();
        }
        LambdaQueryWrapper<UserToolEntity> wrapper = Wrappers.<UserToolEntity>lambdaQuery()
                .in(UserToolEntity::getToolId, toolIds);
        List<UserToolEntity> userToolEntities = userToolRepository.selectList(wrapper);

        // 根据 userToolEntities 进行 toolId 分组，key toolId，value 是分组数量
        Map<String, Long> toolInstallMap = userToolEntities.stream()
                .collect(Collectors.groupingBy(UserToolEntity::getToolId, Collectors.counting()));

        return toolInstallMap;
    }

    /** 获取用户已安装的工具。
     * <p>
     * 软降级：若 {@code toolIds} 中有未安装的（被删除/未安装），记 warn 日志后跳过这些 ID，
     * 不再抛异常阻塞 chat 入口——否则一个 agent 配置残留的旧 toolId 会让整个 agent 无法对话。
     * </p>
     *
     * @param toolIds 工具版本id列表
     * @param userId 用户id
     * @return 实际已安装的工具列表（缺失的会被过滤掉） */
    public List<UserToolEntity> getInstallTool(List<String> toolIds, String userId) {
        if (toolIds == null || toolIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<UserToolEntity> userToolEntities = userToolRepository.selectList(Wrappers.<UserToolEntity>lambdaQuery()
                .in(UserToolEntity::getToolId, toolIds).eq(UserToolEntity::getUserId, userId));

        // 软降级：缺失的 toolId 记 warn 后过滤掉，避免阻塞 chat
        Set<String> installedIds = userToolEntities.stream()
                .map(UserToolEntity::getToolId).collect(Collectors.toSet());
        List<String> missing = toolIds.stream()
                .filter(id -> !installedIds.contains(id)).collect(Collectors.toList());
        if (!missing.isEmpty()) {
            log.warn("Agent 配置的工具未安装或已被删除，已自动跳过: missing={}, userId={}", missing, userId);
        }
        return userToolEntities;
    }

    /** 根据工具ID列表获取用户安装的工具
     * 
     * @param userId 用户ID
     * @param toolIds 工具ID列表
     * @return 用户安装的工具列表 */
    public List<UserToolEntity> getUserToolsByIds(String userId, List<String> toolIds) {
        if (toolIds == null || toolIds.isEmpty()) {
            return new ArrayList<>();
        }

        LambdaQueryWrapper<UserToolEntity> wrapper = Wrappers.<UserToolEntity>lambdaQuery()
                .eq(UserToolEntity::getUserId, userId).in(UserToolEntity::getToolId, toolIds);

        return userToolRepository.selectList(wrapper);
    }
}

package com.blog.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.system.entity.SystemConfig;
import com.blog.system.mapper.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 系统配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemConfigService {

    private final SystemConfigMapper configMapper;
    private final Map<String, ConfigCacheEntry> valueCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MILLIS = 30_000L;
    private static final String MASKED_VALUE = "******";
    private static final Set<String> SENSITIVE_CONFIG_KEYS = Set.of(
            "mail_password",
            "crawler_integration_token"
    );
    private static final Map<String, String> ENV_FALLBACK_KEYS = Map.of(
            "mail_smtp_host", "MAIL_SMTP_HOST",
            "mail_smtp_port", "MAIL_SMTP_PORT",
            "mail_username", "MAIL_USERNAME",
            "mail_password", "MAIL_PASSWORD",
            "mail_ssl_enabled", "MAIL_SSL_ENABLED",
            "mail_from_name", "MAIL_FROM_NAME",
            "mail_code_expire_minutes", "MAIL_CODE_EXPIRE_MINUTES"
    );

    /**
     * 查询所有配置
     */
    public List<SystemConfig> listAll() {
        return configMapper.selectList(
                new LambdaQueryWrapper<SystemConfig>().orderByAsc(SystemConfig::getId));
    }

    /**
     * 查询所有配置（管理端展示视图，敏感值掩码）
     */
    public List<SystemConfig> listAllForAdminView() {
        List<SystemConfig> configs = listAll();
        List<SystemConfig> result = new ArrayList<>(configs.size());
        for (SystemConfig config : configs) {
            SystemConfig safe = new SystemConfig();
            safe.setId(config.getId());
            safe.setConfigKey(config.getConfigKey());
            safe.setDescription(config.getDescription());
            safe.setCreateTime(config.getCreateTime());
            safe.setUpdateTime(config.getUpdateTime());
            if (isSensitiveKey(config.getConfigKey())) {
                safe.setConfigValue(maskSensitiveValue(config.getConfigValue()));
            } else {
                safe.setConfigValue(config.getConfigValue());
            }
            result.add(safe);
        }
        return result;
    }

    /**
     * 根据 key 获取配置值
     */
    public String getValue(String key) {
        ConfigCacheEntry cached = valueCache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.cachedAt <= CACHE_TTL_MILLIS) {
            return cached.value;
        }

        String envValue = readEnvFallback(key);
        if (envValue != null) {
            valueCache.put(key, new ConfigCacheEntry(envValue, now));
            return envValue;
        }

        SystemConfig config = configMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
        String value = config != null ? config.getConfigValue() : null;
        valueCache.put(key, new ConfigCacheEntry(value, now));
        return value;
    }

    /**
     * 根据 key 获取 int 配置值
     */
    public int getIntValue(String key, int defaultValue) {
        String val = getValue(key);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 批量更新配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void batchUpdate(Map<String, String> configs) {
        for (Map.Entry<String, String> entry : configs.entrySet()) {
            SystemConfig existing = configMapper.selectOne(
                    new LambdaQueryWrapper<SystemConfig>()
                            .eq(SystemConfig::getConfigKey, entry.getKey()));
            if (existing == null) {
                throw new BusinessException(ResultCode.NOT_FOUND,
                        "配置项不存在: " + entry.getKey());
            }

            // 敏感配置在管理端以掩码形式展示，若前端未修改直接回传掩码则保持原值
            if (isSensitiveKey(entry.getKey()) && MASKED_VALUE.equals(entry.getValue())) {
                continue;
            }

            existing.setConfigValue(entry.getValue());
            configMapper.updateById(existing);
            valueCache.remove(entry.getKey());
        }
        log.info("系统配置批量更新: keys={}", configs.keySet());
    }

    /**
     * 以 Map 形式返回所有配置
     */
    public Map<String, String> getConfigMap() {
        return listAll().stream().collect(
                Collectors.toMap(SystemConfig::getConfigKey, c ->
                        c.getConfigValue() != null ? c.getConfigValue() : ""));
    }

    /**
     * 创建配置项（如果不存在），已存在则不修改
     */
    public void createIfAbsent(String key, String value, String description) {
        SystemConfig existing = configMapper.selectOne(
                new LambdaQueryWrapper<SystemConfig>().eq(SystemConfig::getConfigKey, key));
        if (existing == null) {
            SystemConfig config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(value);
            config.setDescription(description);
            configMapper.insert(config);
            log.info("系统配置创建: key={}, value={}", key, value);
            valueCache.remove(key);
        }
    }

    private record ConfigCacheEntry(String value, long cachedAt) {
    }

    private boolean isSensitiveKey(String key) {
        return key != null && SENSITIVE_CONFIG_KEYS.contains(key);
    }

    private String readEnvFallback(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        String envKey = ENV_FALLBACK_KEYS.get(key);
        if (envKey == null) {
            return null;
        }
        String envValue = System.getenv(envKey);
        if (envValue == null || envValue.isBlank()) {
            return null;
        }
        return envValue;
    }

    private String maskSensitiveValue(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return MASKED_VALUE;
    }
}

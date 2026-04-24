package com.blog.api.crawler.auth;

import cn.hutool.core.util.StrUtil;
import com.blog.common.exception.BusinessException;
import com.blog.common.result.ResultCode;
import com.blog.system.service.SystemConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CrawlerIntegrationAuthService {

    public static final String CONFIG_CRAWLER_INGEST_ENABLED = "crawler_ingest_enabled";
    public static final String CONFIG_CRAWLER_INTEGRATION_TOKEN = "crawler_integration_token";

    private final SystemConfigService systemConfigService;

    public void checkIngestionEnabled() {
        String enabled = systemConfigService.getValue(CONFIG_CRAWLER_INGEST_ENABLED);
        if (!"true".equalsIgnoreCase(enabled)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "采集入库功能未开启");
        }
    }

    public void checkIntegrationCredential(String crawlerToken, String deviceId) {
        if (StrUtil.isBlank(crawlerToken) || StrUtil.isBlank(deviceId)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "缺少采集集成鉴权参数");
        }

        String expectedToken = systemConfigService.getValue(CONFIG_CRAWLER_INTEGRATION_TOKEN);
        if (StrUtil.isBlank(expectedToken)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "采集集成令牌未配置");
        }

        if (!expectedToken.equals(crawlerToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "采集集成令牌无效");
        }
    }
}

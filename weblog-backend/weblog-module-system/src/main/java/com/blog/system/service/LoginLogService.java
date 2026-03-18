package com.blog.system.service;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.system.dto.LoginLogVO;
import com.blog.system.entity.LoginLog;
import com.blog.system.mapper.LoginLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 登录日志服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLogService {

    private final LoginLogMapper loginLogMapper;

    /**
     * 记录登录日志（异步）
     */
    @Async
    public void recordLogin(Long userId, String email, String loginType, String result, String failReason, String ip, String userAgent) {
        try {
            LoginLog loginLog = new LoginLog();
            loginLog.setUserId(userId);
            loginLog.setEmail(email);
            loginLog.setLoginType(loginType);
            loginLog.setResult(result);
            loginLog.setFailReason(failReason);
            loginLog.setIp(ip);
            loginLog.setUserAgent(userAgent);
            loginLog.setCreateTime(LocalDateTime.now());
            loginLogMapper.insert(loginLog);
        } catch (Exception e) {
            log.error("记录登录日志失败", e);
        }
    }

    /**
     * 查询当前用户的登录日志
     */
    public IPage<LoginLogVO> getMyLoginLogs(int pageNum, int pageSize) {
        Long userId = StpUtil.getLoginIdAsLong();
        Page<LoginLog> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getUserId, userId)
                .orderByDesc(LoginLog::getCreateTime);

        IPage<LoginLog> result = loginLogMapper.selectPage(page, wrapper);

        // 转换为 VO
        Page<LoginLogVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        voPage.setRecords(result.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    /**
     * 查询所有用户的登录日志（管理端）
     */
    public IPage<LoginLogVO> getAllLoginLogs(int pageNum, int pageSize, String email, String loginType, String result) {
        Page<LoginLog> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<LoginLog> wrapper = new LambdaQueryWrapper<LoginLog>()
                .like(email != null, LoginLog::getEmail, email)
                .eq(loginType != null, LoginLog::getLoginType, loginType)
                .eq(result != null, LoginLog::getResult, result)
                .orderByDesc(LoginLog::getCreateTime);

        IPage<LoginLog> resultPage = loginLogMapper.selectPage(page, wrapper);

        Page<LoginLogVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(resultPage.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    /**
     * 清理指定保留天数之前的登录日志
     */
    public int cleanupOldLogs(int retentionDays) {
        int safeRetentionDays = Math.max(retentionDays, 1);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(safeRetentionDays);
        int deleted = loginLogMapper.delete(new LambdaQueryWrapper<LoginLog>()
                .lt(LoginLog::getCreateTime, cutoff));
        log.info("清理登录日志完成: retentionDays={}, cutoff={}, deleted={}", safeRetentionDays, cutoff, deleted);
        return deleted;
    }

    /**
     * 统计时间窗口内失败登录次数
     */
    public long countFailedLoginsSince(LocalDateTime since) {
        return loginLogMapper.selectCount(new LambdaQueryWrapper<LoginLog>()
                .eq(LoginLog::getResult, "failed")
                .ge(LoginLog::getCreateTime, since));
    }

    /**
     * 查询时间窗口内失败次数较高的 IP
     */
    public List<Map<String, Object>> listTopFailedIps(LocalDateTime since, int threshold, int limit) {
        int safeThreshold = Math.max(threshold, 1);
        int safeLimit = Math.max(limit, 1);
        return loginLogMapper.selectTopFailedIps(since, safeThreshold, safeLimit);
    }

    private LoginLogVO toVO(LoginLog log) {
        return LoginLogVO.builder()
                .id(log.getId())
                .userId(log.getUserId())
                .email(log.getEmail())
                .loginType(log.getLoginType())
                .result(log.getResult())
                .failReason(log.getFailReason())
                .ip(log.getIp())
                .userAgent(log.getUserAgent())
                .createTime(log.getCreateTime())
                .build();
    }
}

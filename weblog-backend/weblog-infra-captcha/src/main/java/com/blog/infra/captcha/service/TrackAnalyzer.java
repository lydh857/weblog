package com.blog.infra.captcha.service;

import com.blog.infra.captcha.model.TrackPoint;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.blog.infra.captcha.constant.CaptchaConstants.*;

/**
 * 滑动轨迹分析器 — 纯函数，无外部依赖，易于测试。
 * <p>
 * 通过多维度分析用户滑动轨迹来区分真人与自动化程序：
 * <ol>
 *   <li>基础校验：轨迹点数、滑动时间范围</li>
 *   <li>时间戳非递减校验</li>
 *   <li>速度方差检测（匀速 → 机器人）</li>
 *   <li>方向变化检测（完全单向 → 机器人）</li>
 * </ol>
 */
@Slf4j
@Component
public class TrackAnalyzer {

    /**
     * 验证滑动轨迹是否为真人行为。
     *
     * @param track 轨迹点列表
     * @return true 表示通过（疑似真人），false 表示拒绝（疑似机器人/伪造）
     */
    public boolean validate(List<TrackPoint> track) {
        if (!checkBasic(track)) {
            log.debug("轨迹校验失败: 基础校验不通过, 点数={}, 时长={}ms",
                    track != null ? track.size() : 0,
                    track != null && track.size() >= 2
                            ? track.get(track.size() - 1).getTimestamp() - track.get(0).getTimestamp() : -1);
            return false;
        }
        if (!checkTimestampMonotonic(track)) {
            log.debug("轨迹校验失败: 时间戳非单调递增");
            return false;
        }
        if (!checkSpeedVariance(track)) {
            log.debug("轨迹校验失败: 速度方差过低（疑似匀速机器人）");
            return false;
        }
        // 方向变化检测已移除：手稳的真人平滑右滑不会有方向变化，
        // 速度方差检测已足够区分机器人（匀速）和真人（加速→减速）。
        return true;
    }

    /**
     * 基础校验：点数 >= MIN_TRACK_POINTS，总时间在 [MIN_SLIDE_TIME_MS, MAX_SLIDE_TIME_MS]
     */
    boolean checkBasic(List<TrackPoint> track) {
        if (track == null || track.size() < MIN_TRACK_POINTS) {
            return false;
        }
        long startTime = track.get(0).getTimestamp();
        long endTime = track.get(track.size() - 1).getTimestamp();
        long duration = endTime - startTime;
        return duration >= MIN_SLIDE_TIME_MS && duration <= MAX_SLIDE_TIME_MS;
    }

    /**
     * 时间戳非递减校验（允许相同时间戳，因为高频 pointermove 事件可能同一毫秒触发多次）
     */
    boolean checkTimestampMonotonic(List<TrackPoint> track) {
        for (int i = 1; i < track.size(); i++) {
            if (track.get(i).getTimestamp() < track.get(i - 1).getTimestamp()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 速度方差检测：计算相邻点间的水平速度，若方差过低则判定为匀速（机器人）。
     * 跳过 dt=0 的点对，避免除零和噪声干扰。
     */
    boolean checkSpeedVariance(List<TrackPoint> track) {
        int n = track.size();
        if (n < 2) {
            return false;
        }

        // 收集有效速度（跳过 dt=0 的点对）
        java.util.List<Double> validSpeeds = new java.util.ArrayList<>();
        for (int i = 0; i < n - 1; i++) {
            double dx = track.get(i + 1).getX() - track.get(i).getX();
            long dt = track.get(i + 1).getTimestamp() - track.get(i).getTimestamp();
            if (dt > 0) {
                validSpeeds.add(dx / dt);
            }
        }

        // 有效速度点太少，跳过此项检查
        if (validSpeeds.size() < 2) {
            return true;
        }

        // 计算均值
        double mean = 0;
        for (double s : validSpeeds) {
            mean += s;
        }
        mean /= validSpeeds.size();

        // 计算方差
        double variance = 0;
        for (double s : validSpeeds) {
            double diff = s - mean;
            variance += diff * diff;
        }
        variance /= validSpeeds.size();

        return variance >= SPEED_VARIANCE_THRESHOLD;
    }

}

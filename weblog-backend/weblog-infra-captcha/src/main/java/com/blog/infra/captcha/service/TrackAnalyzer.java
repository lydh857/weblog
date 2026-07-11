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
 *   <li>首末点与水平位移校验（无位移/异常起点 → 机器人）</li>
 *   <li>单步位移比例校验（瞬移跳变 → 机器人）</li>
 *   <li>速度方差检测（匀速 → 机器人）</li>
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
        if (!checkEffectiveTiming(track)) {
            log.debug("轨迹校验失败: 有效时间间隔不足");
            return false;
        }
        if (!checkEdgePoints(track)) {
            log.debug("轨迹校验失败: 首末点异常");
            return false;
        }
        if (!checkHorizontalDisplacement(track)) {
            log.debug("轨迹校验失败: 水平位移不足");
            return false;
        }
        if (!checkSingleStepDisplacement(track)) {
            log.debug("轨迹校验失败: 存在异常跳变");
            return false;
        }
        if (!checkSpeedVariance(track)) {
            log.debug("轨迹校验失败: 速度方差过低（疑似匀速机器人）");
            return false;
        }
        if (!checkAccelerationProfile(track)) {
            log.debug("轨迹校验失败: 缺少合理的加减速特征");
            return false;
        }
        if (!checkSyntheticPattern(track)) {
            log.debug("轨迹校验失败: 轨迹特征过于机械化");
            return false;
        }
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
     * 有效时间间隔校验：允许少量同毫秒事件，但拒绝大量零间隔伪造轨迹。
     */
    boolean checkEffectiveTiming(List<TrackPoint> track) {
        if (track == null || track.size() < MIN_TRACK_POINTS) {
            return false;
        }

        int positiveIntervals = 0;
        int zeroIntervals = 0;
        for (int i = 0; i < track.size() - 1; i++) {
            long dt = track.get(i + 1).getTimestamp() - track.get(i).getTimestamp();
            if (dt > 0) {
                positiveIntervals++;
            } else if (dt == 0) {
                zeroIntervals++;
            }
        }

        int totalIntervals = track.size() - 1;
        int minPositiveIntervals = Math.min(4, totalIntervals);
        return positiveIntervals >= minPositiveIntervals && zeroIntervals <= totalIntervals / 2;
    }

    /**
     * 首末点校验：首点应靠近 0，末点应大于首点。
     */
    boolean checkEdgePoints(List<TrackPoint> track) {
        if (track == null || track.isEmpty()) {
            return false;
        }
        TrackPoint first = track.get(0);
        TrackPoint last = track.get(track.size() - 1);
        if (Math.abs(first.getX()) > TRACK_START_X_TOLERANCE) {
            return false;
        }
        return last.getX() > first.getX();
    }

    /**
     * 最小水平位移校验：避免极短轨迹伪造。
     */
    boolean checkHorizontalDisplacement(List<TrackPoint> track) {
        if (track == null || track.isEmpty()) {
            return false;
        }
        double displacement = track.get(track.size() - 1).getX() - track.get(0).getX();
        return displacement >= MIN_HORIZONTAL_DISPLACEMENT;
    }

    /**
     * 单步跳变校验：若单步位移占整体位移比例过高，判定为异常。
     */
    boolean checkSingleStepDisplacement(List<TrackPoint> track) {
        if (track == null || track.size() < 2) {
            return false;
        }
        double totalDisplacement = track.get(track.size() - 1).getX() - track.get(0).getX();
        if (totalDisplacement <= 0) {
            return false;
        }

        double maxStep = 0;
        for (int i = 0; i < track.size() - 1; i++) {
            double dx = Math.abs(track.get(i + 1).getX() - track.get(i).getX());
            if (dx > maxStep) {
                maxStep = dx;
            }
        }

        return maxStep <= totalDisplacement * MAX_SINGLE_STEP_DISPLACEMENT_RATIO;
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

        // 有效速度点太少，说明轨迹时间分布异常。
        if (validSpeeds.size() < 2) {
            return false;
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

    /**
     * 加减速特征校验：真人轨迹通常存在"先加速、后减速"的速度轮廓。
     * 放宽判定：只要存在明显的速度变化（加速或减速）即可通过。
     */
    boolean checkAccelerationProfile(List<TrackPoint> track) {
        if (track == null || track.size() < 3) {
            return false;
        }

        java.util.List<Double> absSpeeds = new java.util.ArrayList<>();
        for (int i = 0; i < track.size() - 1; i++) {
            double dx = track.get(i + 1).getX() - track.get(i).getX();
            long dt = track.get(i + 1).getTimestamp() - track.get(i).getTimestamp();
            if (dt > 0) {
                absSpeeds.add(Math.abs(dx / dt));
            }
        }

        // 有效速度点太少时跳过此校验（点少说明滑动很快，更不像机器人）
        if (absSpeeds.size() < 4) {
            return true;
        }

        // 计算峰值速度和整体均值
        double peak = 0;
        double sum = 0;
        for (double s : absSpeeds) {
            sum += s;
            if (s > peak) peak = s;
        }
        double meanSpeed = sum / absSpeeds.size();

        // 只要存在明显速度起伏（峰值至少比均值高15%），就判定为真人特征
        // 匀速机器人脚本的峰值≈均值，比率接近1.0
        if (peak >= meanSpeed * 1.15) {
            return true;
        }

        // 或者：前半程速度明显高于后半程（自然减速），也判定为真人
        int half = Math.max(2, absSpeeds.size() / 2);
        double firstHalf = mean(absSpeeds, 0, half);
        double secondHalf = mean(absSpeeds, absSpeeds.size() - half, absSpeeds.size());
        if (firstHalf > 0 && secondHalf < firstHalf * 0.85) {
            return true;
        }

        // 速度绝对值很低（慢速滑动）也放行，慢速操作不可能是脚本
        if (meanSpeed < 0.15) {
            return true;
        }

        log.debug("轨迹校验失败: 缺少加减速特征, peak={}, meanSpeed={}, firstHalf={}, secondHalf={}",
                String.format("%.4f", peak), String.format("%.4f", meanSpeed),
                String.format("%.4f", firstHalf), String.format("%.4f", secondHalf));
        return false;
    }

    /**
     * 机械化特征校验：时间间隔过于固定且纵向扰动极低，同时首尾速度接近，判定为脚本轨迹。
     * 放宽纵向扰动阈值：桌面鼠标几乎无Y抖动，不能仅靠yRange判定。
     */
    boolean checkSyntheticPattern(List<TrackPoint> track) {
        if (track == null || track.size() < 8) {
            return true;
        }

        java.util.List<Long> intervals = new java.util.ArrayList<>();
        java.util.List<Double> absSpeeds = new java.util.ArrayList<>();
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;

        for (TrackPoint point : track) {
            minY = Math.min(minY, point.getY());
            maxY = Math.max(maxY, point.getY());
        }

        for (int i = 0; i < track.size() - 1; i++) {
            long dt = track.get(i + 1).getTimestamp() - track.get(i).getTimestamp();
            if (dt <= 0) {
                continue;
            }
            double dx = track.get(i + 1).getX() - track.get(i).getX();
            intervals.add(dt);
            absSpeeds.add(Math.abs(dx / dt));
        }

        if (intervals.size() < 4 || absSpeeds.size() < 4) {
            return true;
        }

        // 时间间隔种类过少（<=2种）且间隔值过大（脚本用固定interval）
        int uniqueIntervalCount = new java.util.HashSet<>(intervals).size();
        boolean intervalRigid = uniqueIntervalCount <= 2;

        // 纵向扰动阈值放宽到2px（桌面鼠标几乎无Y抖动但仍属于真人）
        double yRange = maxY - minY;
        boolean verticalStable = yRange < 2.0;

        int segment = Math.max(2, absSpeeds.size() / 3);
        double headMean = mean(absSpeeds, 0, segment);
        double tailMean = mean(absSpeeds, absSpeeds.size() - segment, absSpeeds.size());

        // 首尾速度几乎完全一致（差值 < 均值的5%）才算"速度平坦"
        double avgSpeed = (headMean + tailMean) / 2;
        boolean speedFlat = avgSpeed > 0 && Math.abs(headMean - tailMean) / avgSpeed < 0.05;

        // 三个条件全部满足才判定为机器人：固定间隔 + 无纵向抖动 + 首尾速度一致
        // 正常桌面鼠标虽然纵向稳定，但时间间隔不会那么规律，速度也不会那么平坦
        boolean isSynthetic = intervalRigid && verticalStable && speedFlat;

        if (isSynthetic) {
            log.debug("轨迹校验失败: 疑似机械化, intervals={}, uniqueIntervals={}, yRange={}, headMean={}, tailMean={}",
                    intervals.size(), uniqueIntervalCount, String.format("%.1f", yRange),
                    String.format("%.4f", headMean), String.format("%.4f", tailMean));
        }
        return !isSynthetic;
    }

    private double mean(java.util.List<Double> values, int startInclusive, int endExclusive) {
        if (values == null || values.isEmpty() || startInclusive >= endExclusive) {
            return 0;
        }

        int start = Math.max(0, startInclusive);
        int end = Math.min(values.size(), endExclusive);
        if (start >= end) {
            return 0;
        }

        double sum = 0;
        for (int i = start; i < end; i++) {
            sum += values.get(i);
        }
        return sum / (end - start);
    }

}
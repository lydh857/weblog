package com.blog.infra.captcha.service;

import com.blog.infra.captcha.model.TrackPoint;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TrackAnalyzerSecurityTest {

    private final TrackAnalyzer trackAnalyzer = new TrackAnalyzer();

    @Test
    void shouldRejectUniformSpeedScriptTrack() {
        List<TrackPoint> track = new ArrayList<>();
        long timestamp = 0;
        for (int i = 0; i < 24; i++) {
            double x = i * 7.0;
            track.add(buildPoint(x, 0, timestamp));
            timestamp += 12;
        }

        assertFalse(trackAnalyzer.validate(track));
    }

    @Test
    void shouldRejectTrackWithLargeJumpStep() {
        List<TrackPoint> track = List.of(
                buildPoint(0, 0, 0),
                buildPoint(4, 0, 60),
                buildPoint(11, 0, 120),
                buildPoint(20, 0, 180),
                buildPoint(151, 0, 240),
                buildPoint(164, 0, 320)
        );

        assertFalse(trackAnalyzer.validate(track));
    }

    @Test
    void shouldAcceptHumanLikeTrack() {
        List<TrackPoint> track = List.of(
                buildPoint(0, 0, 0),
                buildPoint(3, 0.7, 32),
                buildPoint(9, -0.5, 64),
                buildPoint(18, 1.1, 96),
                buildPoint(31, -0.4, 128),
                buildPoint(47, 1.6, 168),
                buildPoint(67, 0.2, 208),
                buildPoint(89, -1.3, 256),
                buildPoint(108, 0.5, 304),
                buildPoint(124, -0.8, 352),
                buildPoint(138, 0.9, 408),
                buildPoint(149, 0.2, 472),
                buildPoint(156, -0.4, 548),
                buildPoint(160, 0.1, 632)
        );

        assertTrue(trackAnalyzer.validate(track));
    }

    @Test
    void shouldKeepSimulatedScriptPassRateLow() {
        int total = 120;
        int accepted = 0;
        Random random = new Random(20260402L);

        for (int i = 0; i < total; i++) {
            if (trackAnalyzer.validate(buildScriptTrack(random))) {
                accepted++;
            }
        }

        assertTrue(accepted <= 12, "模拟脚本通过率过高: " + accepted + "/" + total);
    }

    private List<TrackPoint> buildScriptTrack(Random random) {
        int points = 24;
        double target = 150 + random.nextInt(25);
        long timestamp = 0;
        List<TrackPoint> track = new ArrayList<>();

        for (int i = 0; i < points; i++) {
            double progress = i / (double) (points - 1);
            double x = target * progress;
            if (i > points * 0.8) {
                x += 3.5;
            }
            x += (i % 2 == 0 ? 0.15 : -0.15);
            double y = i % 3 == 0 ? 0 : 0.1;

            if (i == 0) {
                x = 0;
            }
            if (i == points - 1) {
                x = target;
            }

            track.add(buildPoint(x, y, timestamp));
            timestamp += 12;
        }

        return track;
    }

    private TrackPoint buildPoint(double x, double y, long timestamp) {
        TrackPoint point = new TrackPoint();
        point.setX(x);
        point.setY(y);
        point.setTimestamp(timestamp);
        return point;
    }
}

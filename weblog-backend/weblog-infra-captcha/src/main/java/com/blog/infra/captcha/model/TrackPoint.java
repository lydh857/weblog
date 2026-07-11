package com.blog.infra.captcha.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TrackPoint {
    @Min(value = -10, message = "轨迹坐标无效")
    @Max(value = 1000, message = "轨迹坐标无效")
    private double x;

    @Min(value = -500, message = "轨迹坐标无效")
    @Max(value = 1000, message = "轨迹坐标无效")
    private double y;

    @Min(value = 0, message = "时间戳无效")
    private long timestamp;
}

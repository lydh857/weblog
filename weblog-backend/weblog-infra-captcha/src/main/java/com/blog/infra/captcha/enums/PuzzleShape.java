package com.blog.infra.captcha.enums;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 拼图形状枚举（60×60 像素坐标）
 */
public enum PuzzleShape {

    SHAPE_RIGHT_BUMP(new int[][]{
        {0, 0}, {42, 0}, {42, 22}, {47, 22}, {50, 25}, {52, 28},
        {50, 31}, {47, 34}, {42, 34}, {42, 56}, {0, 56}
    }),
    SHAPE_BOTTOM_BUMP(new int[][]{
        {0, 0}, {56, 0}, {56, 42}, {34, 42}, {34, 47}, {31, 50},
        {28, 52}, {25, 50}, {22, 47}, {22, 42}, {0, 42}
    }),
    SHAPE_CORNER_BUMP(new int[][]{
        {0, 0}, {42, 0}, {42, 18}, {47, 18}, {50, 21}, {52, 24},
        {50, 27}, {47, 30}, {42, 30}, {42, 42}, {30, 42}, {30, 47},
        {27, 50}, {24, 52}, {21, 50}, {18, 47}, {18, 42}, {0, 42}
    }),
    SHAPE_LEFT_INDENT_RIGHT_BUMP(new int[][]{
        {0, 0}, {42, 0}, {42, 22}, {47, 22}, {50, 25}, {52, 28},
        {50, 31}, {47, 34}, {42, 34}, {42, 56}, {0, 56}, {0, 34},
        {5, 34}, {8, 31}, {10, 28}, {8, 25}, {5, 22}, {0, 22}
    }),
    SHAPE_TOP_INDENT_BOTTOM_BUMP(new int[][]{
        {0, 0}, {22, 0}, {22, 5}, {25, 8}, {28, 10}, {31, 8},
        {34, 5}, {34, 0}, {56, 0}, {56, 42}, {34, 42}, {34, 47},
        {31, 50}, {28, 52}, {25, 50}, {22, 47}, {22, 42}, {0, 42}
    }),
    SHAPE_RECTANGLE(new int[][]{
        {0, 0}, {50, 0}, {50, 50}, {0, 50}
    }),
    SHAPE_ROUNDED(new int[][]{
        {5, 0}, {45, 0}, {50, 5}, {50, 45}, {45, 50}, {5, 50}, {0, 45}, {0, 5}
    }),
    SHAPE_HEXAGON(new int[][]{
        {15, 0}, {45, 0}, {55, 28}, {45, 55}, {15, 55}, {5, 28}
    });

    private final int[][] points;

    PuzzleShape(int[][] points) {
        this.points = points;
    }

    public int[][] getPoints() {
        return points;
    }

    public static PuzzleShape random() {
        PuzzleShape[] shapes = values();
        return shapes[ThreadLocalRandom.current().nextInt(shapes.length)];
    }

    public static PuzzleShape randomExcept(PuzzleShape exclude) {
        PuzzleShape[] shapes = values();
        if (shapes.length <= 1) return shapes[0];
        PuzzleShape result;
        do {
            result = shapes[ThreadLocalRandom.current().nextInt(shapes.length)];
        } while (result == exclude);
        return result;
    }
}

package com.blog.infra.captcha.service;

import com.blog.infra.captcha.enums.PuzzleShape;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static com.blog.infra.captcha.constant.CaptchaConstants.*;

/**
 * 验证码图片处理服务。
 * <p>
 * 负责预加载背景图片、生成拼图块、绘制缺口/干扰缺口、添加噪点、Base64 编码。
 */
@Slf4j
@Service
public class CaptchaImageService {

    private final List<BufferedImage> backgroundImages = new ArrayList<>();

    // ===== 生命周期 =====

    @PostConstruct
    public void loadImages() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:" + IMAGE_PATH + "*.jpg");

            for (Resource resource : resources) {
                try (InputStream is = resource.getInputStream()) {
                    BufferedImage img = ImageIO.read(is);
                    if (img != null) {
                        // 预缩放到目标尺寸，避免每次生成时重复缩放
                        BufferedImage resized = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = resized.createGraphics();
                        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g.drawImage(img, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, null);
                        g.dispose();
                        backgroundImages.add(resized);
                        log.info("加载验证码背景图片: {}", resource.getFilename());
                    }
                } catch (IOException e) {
                    log.error("加载背景图片失败: {}", resource.getFilename(), e);
                }
            }
            log.info("验证码背景图片预加载完成，共 {} 张", backgroundImages.size());
        } catch (IOException e) {
            log.error("预加载验证码背景图片失败", e);
        }
    }

    // ===== 公开方法 =====

    /**
     * 获取随机背景图（返回预缩放图片的副本，避免修改原图）。
     */
    public BufferedImage getRandomBackground() {
        if (backgroundImages.isEmpty()) {
            throw new RuntimeException("没有可用的验证码背景图片");
        }
        BufferedImage original = backgroundImages.get(
                ThreadLocalRandom.current().nextInt(backgroundImages.size()));

        // 直接复制预缩放的图片，无需再次缩放
        BufferedImage copy = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(original, 0, 0, null);
        g.dispose();
        return copy;
    }

    /**
     * 从背景图中裁剪拼图块，并在背景图上绘制正确缺口。
     *
     * @return 拼图块 PNG Base64
     */
    public String generatePuzzleImage(BufferedImage background, int targetX, int targetY, PuzzleShape shape) {
        Path2D.Double path = createPath(shape);

        // 裁剪拼图块
        BufferedImage puzzle = new BufferedImage(PUZZLE_WIDTH, PUZZLE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = puzzle.createGraphics();
        pg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        pg.setClip(path);
        pg.drawImage(background, -targetX, -targetY, null);
        pg.setClip(null);
        // 拼图块白色边框
        pg.setColor(new Color(255, 255, 255, 100));
        pg.setStroke(new BasicStroke(2));
        pg.draw(path);
        pg.dispose();

        // 在背景图上绘制正确缺口
        drawCutout(background, targetX, targetY, path);

        return imageToBase64Png(puzzle);
    }

    /**
     * 在背景图上绘制干扰缺口（仅视觉效果，不裁剪拼图块）。
     */
    public void drawDecoyCutout(BufferedImage background, int x, int y, PuzzleShape shape) {
        drawCutout(background, x, y, createPath(shape));
    }

    /**
     * 添加噪点：随机选取 2%-5% 的像素，对 RGB 值添加 ±10-30 的偏移。
     */
    public void addNoise(BufferedImage image) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int totalPixels = image.getWidth() * image.getHeight();
        int noiseCount = totalPixels * rng.nextInt(2, 6) / 100; // 2%-5%

        for (int i = 0; i < noiseCount; i++) {
            int px = rng.nextInt(image.getWidth());
            int py = rng.nextInt(image.getHeight());
            int rgb = image.getRGB(px, py);

            int a = (rgb >> 24) & 0xFF;
            int r = clamp(((rgb >> 16) & 0xFF) + rng.nextInt(-30, 31));
            int g = clamp(((rgb >> 8) & 0xFF) + rng.nextInt(-30, 31));
            int b = clamp((rgb & 0xFF) + rng.nextInt(-30, 31));

            image.setRGB(px, py, (a << 24) | (r << 16) | (g << 8) | b);
        }
    }

    /**
     * 背景图编码为 JPEG Base64（质量 0.75，减小体积）。
     */
    public String imageToBase64Jpeg(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // 转为 RGB（JPEG 不支持 ARGB）
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.drawImage(image, 0, 0, null);
            g.dispose();

            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
            if (!writers.hasNext()) {
                throw new RuntimeException("JPEG ImageWriter 不可用");
            }
            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.75f);

            try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(rgb, null, null), param);
            }
            writer.dispose();

            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("JPEG Base64 编码失败", e);
        }
    }

    /**
     * 拼图块编码为 PNG Base64（需要透明通道）。
     */
    public String imageToBase64Png(BufferedImage image) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("PNG Base64 编码失败", e);
        }
    }

    // ===== 内部方法 =====

    /**
     * 在背景图上绘制缺口（半透明遮罩 + 白色边框）。
     */
    private void drawCutout(BufferedImage background, int x, int y, Path2D.Double path) {
        Graphics2D g = background.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.translate(x, y);

        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.6f));
        g.setColor(new Color(0, 0, 0, 150));
        g.fill(path);

        g.setComposite(AlphaComposite.SrcOver);
        g.setColor(new Color(255, 255, 255, 80));
        g.setStroke(new BasicStroke(2f));
        g.draw(path);

        g.dispose();
    }

    private Path2D.Double createPath(PuzzleShape shape) {
        Path2D.Double path = new Path2D.Double();
        int[][] points = shape.getPoints();
        if (points.length > 0) {
            path.moveTo(points[0][0], points[0][1]);
            for (int i = 1; i < points.length; i++) {
                path.lineTo(points[i][0], points[i][1]);
            }
            path.closePath();
        }
        return path;
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}

package com.blog.api;

import io.github.cdimascio.dotenv.Dotenv;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 博客系统 API 启动类
 */
@SpringBootApplication
@ComponentScan(basePackages = "com.blog")
@MapperScan({"com.blog.**.mapper", "com.blog.infra.security.audit"})
@EnableScheduling
@EnableAsync
@EnableCaching
public class BlogApiApplication {

    public static void main(String[] args) {
        // 手动加载 .env 文件（不依赖 dotenv-java 库）
        loadEnvFile();

        SpringApplication.run(BlogApiApplication.class, args);
    }

    private static void loadEnvFile() {
        String currentDir = System.getProperty("user.dir");
        System.out.println("[.env] Current directory: " + currentDir);

        // 尝试多个可能的 .env 文件路径
        String[] possiblePaths = {
            ".env",
            "./weblog-backend/.env",
            "../.env"
        };

        for (String path : possiblePaths) {
            java.io.File envFile = new java.io.File(path);
            System.out.println("[.env] Trying: " + envFile.getAbsolutePath() + " (exists: " + envFile.exists() + ")");
            
            if (envFile.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
                    String line;
                    int count = 0;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        // 跳过注释和空行
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        // 解析 KEY=VALUE 格式
                        int equalsIndex = line.indexOf('=');
                        if (equalsIndex > 0) {
                            String key = line.substring(0, equalsIndex).trim();
                            String value = line.substring(equalsIndex + 1).trim();
                            System.setProperty(key, value);
                            count++;
                            
                            // 特别输出 RSA 私钥加载信息
                            if ("RSA_PRIVATE_KEY".equals(key)) {
                                System.out.println("[.env] RSA_PRIVATE_KEY loaded, length: " + value.length());
                            }
                        }
                    }
                    System.out.println("[.env] Successfully loaded " + count + " variables from: " + envFile.getAbsolutePath());
                    return; // 成功加载后退出
                } catch (Exception e) {
                    System.err.println("[.env] Failed to read file: " + e.getMessage());
                }
            }
        }
        
        System.err.println("[.env] No .env file found in any of the expected locations!");
    }
}

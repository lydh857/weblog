package com.blog.api;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@SpringBootApplication
@ComponentScan(basePackages = "com.blog")
@MapperScan({"com.blog.**.mapper", "com.blog.infra.security.audit"})
@EnableScheduling
@EnableAsync
@EnableCaching
public class BlogApiApplication {

    public static void main(String[] args) {
        loadEnvFile();
        SpringApplication.run(BlogApiApplication.class, args);
    }

    private static void loadEnvFile() {
        String currentDir = System.getProperty("user.dir");
        log.info("[.env] Current directory: {}", currentDir);

        String[] possiblePaths = {
            ".env",
            "./weblog-backend/.env",
            "../.env"
        };

        for (String path : possiblePaths) {
            java.io.File envFile = new java.io.File(path);
            log.debug("[.env] Trying: {} (exists: {})", envFile.getAbsolutePath(), envFile.exists());

            if (envFile.exists()) {
                try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(envFile))) {
                    String line;
                    int count = 0;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) {
                            continue;
                        }
                        int equalsIndex = line.indexOf('=');
                        if (equalsIndex > 0) {
                            String key = line.substring(0, equalsIndex).trim();
                            String value = line.substring(equalsIndex + 1).trim();
                            System.setProperty(key, value);
                            count++;
                        }
                    }
                    log.info("[.env] Successfully loaded {} variables from: {}", count, envFile.getAbsolutePath());
                    return;
                } catch (Exception e) {
                    log.error("[.env] Failed to read file: {}", e.getMessage());
                }
            }
        }

        log.warn("[.env] No .env file found in any of the expected locations!");
    }
}

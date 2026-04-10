package com.blog.api.scheduler;

import com.blog.infra.oss.StorageFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 定时清理临时目录中超过 24 小时的文件
 * 编辑文章时图片先上传到 temp/ 目录，发布时迁移到 images/ 目录
 * 未发布的临时文件需要定期清理
 */
@Slf4j
@Component
public class TempFileCleanupScheduler {

    @Autowired
    private StorageFacade storageFacade;

    /**
     * 每天凌晨 4 点清理前天及更早的临时文件
     */
    @Scheduled(cron = "0 0 4 * * ?")
    public synchronized void cleanupTempFiles() {
        if (!storageFacade.isStorageEnabled()) return;

        try {
            String todayPrefix = "temp/" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
            List<String> allTempKeys = storageFacade.listObjects("temp/");

            List<String> toDelete = allTempKeys.stream()
                    .filter(key -> !key.startsWith(todayPrefix))
                    .toList();

            if (!toDelete.isEmpty()) {
                storageFacade.deleteObjects(toDelete);
                log.info("清理临时文件完成，共删除 {} 个文件", toDelete.size());
            } else {
                log.debug("无需清理的临时文件");
            }
        } catch (Exception e) {
            log.error("清理临时文件失败", e);
        }
    }
}

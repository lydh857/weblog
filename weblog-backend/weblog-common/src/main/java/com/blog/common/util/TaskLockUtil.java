package com.blog.common.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时任务同步锁工具（单机部署）
 * 防止同一任务并发执行
 */
public final class TaskLockUtil {

    private TaskLockUtil() {}

    private static final ConcurrentHashMap<String, AtomicBoolean> LOCKS = new ConcurrentHashMap<>();

    /**
     * 尝试获取锁
     * @param taskName 任务名称
     * @return true 获取成功，false 任务正在执行
     */
    public static boolean tryLock(String taskName) {
        AtomicBoolean lock = LOCKS.computeIfAbsent(taskName, k -> new AtomicBoolean(false));
        return lock.compareAndSet(false, true);
    }

    /**
     * 释放锁
     */
    public static void unlock(String taskName) {
        AtomicBoolean lock = LOCKS.get(taskName);
        if (lock != null) {
            lock.set(false);
        }
    }
}

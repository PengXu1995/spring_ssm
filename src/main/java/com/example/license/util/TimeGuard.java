package com.example.license.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 时间单调递增守护：将 lastSeen 时间戳持久化到本地文件，
 * 防止通过回拨系统时钟绕过 License 过期校验。
 * <p>
 * 文件路径默认为 {@code ${user.home}/.license_guard/last_seen.ts}，
 * 可通过系统属性 {@code license.guard.path} 覆盖。
 */
public class TimeGuard {

    private static final Logger log = LoggerFactory.getLogger(TimeGuard.class);
    private static final ReentrantLock LOCK = new ReentrantLock();

    private final Path storePath;

    public TimeGuard() {
        String customPath = System.getProperty("license.guard.path");
        if (customPath != null && !customPath.isBlank()) {
            this.storePath = Path.of(customPath);
        } else {
            this.storePath = Path.of(System.getProperty("user.home"), ".license_guard", "last_seen.ts");
        }
    }

    /** 仅用于测试，指定存储路径 */
    public TimeGuard(Path storePath) {
        this.storePath = storePath;
    }

    /**
     * 读取持久化的 lastSeen 时间戳（Unix 秒）；文件不存在时返回 0。
     */
    public long readLastSeen() {
        LOCK.lock();
        try {
            if (!Files.exists(storePath)) {
                return 0L;
            }
            String content = Files.readString(storePath, StandardCharsets.UTF_8).trim();
            return Long.parseLong(content);
        } catch (Exception e) {
            log.warn("读取 lastSeen 时间戳失败，将使用 0：{}", e.getMessage());
            return 0L;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * 将 nowSecs 持久化为新的 lastSeen 时间戳。
     * 仅当 nowSecs >= lastSeen 时才更新（保证单调递增）。
     *
     * @return 更新是否成功
     */
    public boolean updateLastSeen(long nowSecs) {
        LOCK.lock();
        try {
            long current = readLastSeenUnsafe();
            if (nowSecs < current) {
                log.warn("拒绝更新 lastSeen：nowSecs={} < current={}", nowSecs, current);
                return false;
            }
            File parent = storePath.toFile().getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.writeString(storePath, String.valueOf(nowSecs),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) {
            log.error("更新 lastSeen 时间戳失败：{}", e.getMessage());
            return false;
        } finally {
            LOCK.unlock();
        }
    }

    private long readLastSeenUnsafe() {
        try {
            if (!Files.exists(storePath)) {
                return 0L;
            }
            return Long.parseLong(Files.readString(storePath, StandardCharsets.UTF_8).trim());
        } catch (Exception e) {
            return 0L;
        }
    }
}

package com.example.license.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

/**
 * TimeGuard 单元测试：持久化 lastSeen、单调递增守护。
 */
class TimeGuardTest {

    @Test
    void readLastSeen_whenNoFile_shouldReturnZero(@TempDir Path tempDir) {
        TimeGuard guard = new TimeGuard(tempDir.resolve("last_seen.ts"));
        assertThat(guard.readLastSeen()).isEqualTo(0L);
    }

    @Test
    void updateAndRead_shouldPersist(@TempDir Path tempDir) {
        TimeGuard guard = new TimeGuard(tempDir.resolve("last_seen.ts"));
        guard.updateLastSeen(1_700_000_000L);
        assertThat(guard.readLastSeen()).isEqualTo(1_700_000_000L);
    }

    @Test
    void updateLastSeen_withLargerValue_shouldUpdate(@TempDir Path tempDir) {
        TimeGuard guard = new TimeGuard(tempDir.resolve("last_seen.ts"));
        guard.updateLastSeen(1_000L);
        boolean updated = guard.updateLastSeen(2_000L);
        assertThat(updated).isTrue();
        assertThat(guard.readLastSeen()).isEqualTo(2_000L);
    }

    @Test
    void updateLastSeen_withSmallerValue_shouldReject(@TempDir Path tempDir) {
        TimeGuard guard = new TimeGuard(tempDir.resolve("last_seen.ts"));
        guard.updateLastSeen(2_000L);
        boolean updated = guard.updateLastSeen(1_000L);  // 回拨
        assertThat(updated).isFalse();
        // 值不变
        assertThat(guard.readLastSeen()).isEqualTo(2_000L);
    }

    @Test
    void updateLastSeen_withSameValue_shouldAccept(@TempDir Path tempDir) {
        TimeGuard guard = new TimeGuard(tempDir.resolve("last_seen.ts"));
        guard.updateLastSeen(1_500L);
        boolean updated = guard.updateLastSeen(1_500L);
        assertThat(updated).isTrue();
    }
}

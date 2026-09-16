package com.zifang.z.msg.core.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 通道限流器 (Phase 3.4)
 * <p>
 * 简单的滑动窗口计数器:每通道每秒最多 N 条。
 * 内存实现,集群下可替换为 Redis 原子 incr。
 */
public class ChannelRateLimiter {

    /**
     * channel -> bucket (per-second)
     */
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    /**
     * 通道默认 qps (channels with explicit limit; fallback)
     */
    private final int defaultPermitsPerSecond;

    public ChannelRateLimiter(int defaultPermitsPerSecond) {
        this.defaultPermitsPerSecond = defaultPermitsPerSecond;
    }

    /**
     * 申请一次配额。返回 true=放行, false=被限流。
     */
    public boolean tryAcquire(String channel) {
        Bucket b = buckets.computeIfAbsent(channel, k -> new Bucket(defaultPermitsPerSecond));
        return b.tryAcquire();
    }

    public void setPermits(String channel, int permitsPerSecond) {
        buckets.computeIfAbsent(channel, k -> new Bucket(permitsPerSecond)).resetTo(permitsPerSecond);
    }

    /**
     * 1秒窗口桶。
     */
    static class Bucket {
        private final AtomicInteger remaining;
        private volatile long windowEnd;
        private volatile int permits;

        Bucket(int permits) {
            this.permits = permits;
            this.remaining = new AtomicInteger(permits);
            this.windowEnd = System.currentTimeMillis() + 1000;
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now >= windowEnd) {
                windowEnd = now + 1000;
                remaining.set(permits);
            }
            int r = remaining.get();
            if (r <= 0) {
                return false;
            }

            return remaining.compareAndSet(r, r - 1);
        }

        void resetTo(int permits) {
            this.permits = permits;
            this.remaining.set(permits);
            this.windowEnd = System.currentTimeMillis() + 1000;
        }
    }
}

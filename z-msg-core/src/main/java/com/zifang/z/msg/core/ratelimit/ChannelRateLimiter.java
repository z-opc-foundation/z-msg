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

    /**
     * 自定义窗口 + 配额的申请（1.1.0 用于"每用户每分钟 N 条"这类非 1 秒窗口）。
     * <p>
     * permits 以调用方配置为准，不再吃 {@code defaultPermitsPerSecond}：否则同一个 key
     * 第一次以 20 建桶、第二次想按 60 限流时会拿到旧桶，配额悄悄变成 20。
     *
     * @param key         计数键，例如 {@code SMS#u1001}
     * @param permits     窗口内允许的次数
     * @param windowMillis 窗口长度
     */
    public boolean tryAcquire(String key, int permits, long windowMillis) {
        if (permits <= 0) {
            return true;
        }
        Bucket b = buckets.computeIfAbsent(key, k -> new Bucket(permits, windowMillis));
        return b.tryAcquireWindowed(permits, windowMillis);
    }

    public void setPermits(String channel, int permitsPerSecond) {
        buckets.computeIfAbsent(channel, k -> new Bucket(permitsPerSecond)).resetTo(permitsPerSecond);
    }

    /**
     * 测试/运维用：清掉某个键的计数
     */
    public void reset(String key) {
        buckets.remove(key);
    }

    public int bucketCount() {
        return buckets.size();
    }

    /**
     * 1秒窗口桶（默认）/ 自定义长度窗口桶。
     */
    static class Bucket {
        private final AtomicInteger remaining;
        private volatile long windowEnd;
        private volatile int permits;
        private volatile long windowMillis;

        Bucket(int permits) {
            this(permits, 1000L);
        }

        Bucket(int permits, long windowMillis) {
            this.permits = permits;
            this.windowMillis = windowMillis;
            this.remaining = new AtomicInteger(permits);
            this.windowEnd = System.currentTimeMillis() + windowMillis;
        }

        synchronized boolean tryAcquire() {
            long now = System.currentTimeMillis();
            if (now >= windowEnd) {
                windowEnd = now + windowMillis;
                remaining.set(permits);
            }
            int r = remaining.get();
            if (r <= 0) {
                return false;
            }

            return remaining.compareAndSet(r, r - 1);
        }

        /**
         * 每次申请都按调用方给的配额校正，避免同 key 换配额时被老桶粘住
         */
        synchronized boolean tryAcquireWindowed(int permits, long windowMillis) {
            if (this.permits != permits || this.windowMillis != windowMillis) {
                this.permits = permits;
                this.windowMillis = windowMillis;
                this.remaining.set(permits);
                this.windowEnd = System.currentTimeMillis() + windowMillis;
            }
            return tryAcquire();
        }

        void resetTo(int permits) {
            this.permits = permits;
            this.remaining.set(permits);
            this.windowEnd = System.currentTimeMillis() + windowMillis;
        }
    }
}

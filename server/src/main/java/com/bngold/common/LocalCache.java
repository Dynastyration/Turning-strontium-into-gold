package com.bngold.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.Map;

/**
 * 单机缓存。技术路线里这几项由 Redis 承担，本机没有 Redis 服务，
 * 换成进程内实现；上线多实例时把这些静态字段替换为 RedisTemplate 的同名方法即可，调用方不用改。
 */
public final class LocalCache {

    /** 传感器最新值：sensorId -> {fieldKey: value} */
    public static final Cache<Long, Map<String, Object>> LATEST =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofDays(1)).maximumSize(100_000).build();

    /** 接收端幂等键 */
    public static final Cache<String, Boolean> IDEM =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).maximumSize(500_000).build();

    /** 报警抑制：key -> [alarmId, 过期时间戳] */
    public static final Cache<String, long[]> ALARM_OPEN =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(12)).maximumSize(100_000).build();

    /** 大屏指标快照 */
    public static final Cache<String, Object> DASH =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(30)).maximumSize(200).build();

    private LocalCache() {
    }
}

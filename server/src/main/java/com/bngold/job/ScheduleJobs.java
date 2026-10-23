package com.bngold.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务：离线判定、分钟聚合。
 * 多实例部署时这两个任务要加分布式锁（技术路线 9.5 的 ShedLock）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduleJobs {

    private final JdbcTemplate jdbc;

    /** 每分钟：last_push_at + 采集周期×3 已过则置离线。 */
    @Scheduled(cron = "0 * * * * ?")
    public void offlineCheck() {
        int n = jdbc.update("""
                UPDATE device
                SET run_status = 0
                WHERE deleted = 0 AND run_status = 1 AND last_push_at IS NOT NULL
                  AND DATEADD(SECOND, collect_period * 3, last_push_at) < SYSDATETIME()
                """);
        if (n > 0) {
            log.info("离线判定：{} 台设备转离线", n);
        }
    }

    /** 每分钟：把上一分钟之前的明细并入分钟聚合表。 */
    @Scheduled(cron = "10 * * * * ?")
    public void aggregateMinute() {
        try {
            int n = jdbc.update("""
                    MERGE sensor_reading_min WITH (HOLDLOCK) AS t
                    USING (
                        SELECT sensor_id, field_id,
                               DATEADD(MINUTE, DATEDIFF(MINUTE, 0, ts), 0) AS ts_min,
                               AVG(value_num) AS val_avg, MIN(value_num) AS val_min,
                               MAX(value_num) AS val_max, COUNT(*) AS sample_cnt
                        FROM sensor_reading
                        WHERE ts >= DATEADD(MINUTE, -5, SYSDATETIME())
                          AND ts <  DATEADD(MINUTE, DATEDIFF(MINUTE, 0, SYSDATETIME()), 0)
                          AND value_num IS NOT NULL
                        GROUP BY sensor_id, field_id, DATEDIFF(MINUTE, 0, ts)
                    ) AS s
                    ON t.sensor_id = s.sensor_id AND t.field_id = s.field_id AND t.ts_min = s.ts_min
                    WHEN MATCHED THEN UPDATE SET val_avg = s.val_avg, val_min = s.val_min,
                                                 val_max = s.val_max, sample_cnt = s.sample_cnt
                    WHEN NOT MATCHED THEN INSERT (sensor_id, field_id, ts_min, val_avg, val_min, val_max, sample_cnt)
                         VALUES (s.sensor_id, s.field_id, s.ts_min, s.val_avg, s.val_min, s.val_max, s.sample_cnt);
                    """);
            if (n > 0) {
                log.debug("分钟聚合写入 {} 行", n);
            }
        } catch (Exception e) {
            log.warn("分钟聚合失败: {}", e.getMessage());
        }
    }

    /** 每小时 05 分：分钟聚合并入小时聚合。 */
    @Scheduled(cron = "0 5 * * * ?")
    public void aggregateHour() {
        try {
            jdbc.update("""
                    MERGE sensor_reading_hour WITH (HOLDLOCK) AS t
                    USING (
                        SELECT sensor_id, field_id,
                               DATEADD(HOUR, DATEDIFF(HOUR, 0, ts_min), 0) AS ts_hour,
                               AVG(val_avg) AS val_avg, MIN(val_min) AS val_min,
                               MAX(val_max) AS val_max, SUM(sample_cnt) AS sample_cnt
                        FROM sensor_reading_min
                        WHERE ts_min >= DATEADD(HOUR, -2, SYSDATETIME())
                        GROUP BY sensor_id, field_id, DATEDIFF(HOUR, 0, ts_min)
                    ) AS s
                    ON t.sensor_id = s.sensor_id AND t.field_id = s.field_id AND t.ts_hour = s.ts_hour
                    WHEN MATCHED THEN UPDATE SET val_avg = s.val_avg, val_min = s.val_min,
                                                 val_max = s.val_max, sample_cnt = s.sample_cnt
                    WHEN NOT MATCHED THEN INSERT (sensor_id, field_id, ts_hour, val_avg, val_min, val_max, sample_cnt)
                         VALUES (s.sensor_id, s.field_id, s.ts_hour, s.val_avg, s.val_min, s.val_max, s.sample_cnt);
                    """);
        } catch (Exception e) {
            log.warn("小时聚合失败: {}", e.getMessage());
        }
    }
}

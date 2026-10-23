package com.bngold.service;

import com.bngold.common.LocalCache;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final JdbcTemplate jdbc;

    @SuppressWarnings("unchecked")
    public Map<String, Object> overview(Long projectId) {
        String key = "overview:" + (projectId == null ? "all" : projectId);
        Object cached = LocalCache.DASH.getIfPresent(key);
        if (cached != null) {
            return (Map<String, Object>) cached;
        }
        String pf = projectId == null ? "" : " AND project_id = " + projectId;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deviceTotal", count("SELECT COUNT(*) FROM device WHERE deleted = 0" + pf));
        m.put("deviceOnline", count("SELECT COUNT(*) FROM device WHERE deleted = 0 AND run_status = 1" + pf));
        m.put("sensorTotal", count("SELECT COUNT(*) FROM sensor WHERE deleted = 0"
                + (projectId == null ? "" : " AND device_id IN (SELECT id FROM device WHERE project_id = " + projectId + ")")));
        m.put("sensorOnline", count("SELECT COUNT(*) FROM sensor WHERE deleted = 0 AND status = 1"
                + (projectId == null ? "" : " AND device_id IN (SELECT id FROM device WHERE project_id = " + projectId + ")")));
        m.put("alarmTotal", count("SELECT COUNT(*) FROM alarm" + (projectId == null ? "" : " WHERE project_id = " + projectId)));
        m.put("alarmOpen", count("SELECT COUNT(*) FROM alarm WHERE status IN (0,1)"
                + (projectId == null ? "" : " AND project_id = " + projectId)));
        m.put("alarmToday", count("SELECT COUNT(*) FROM alarm WHERE first_ts >= CAST(SYSDATETIME() AS DATE)"
                + (projectId == null ? "" : " AND project_id = " + projectId)));
        m.put("monitorTotal", count("SELECT COUNT(*) FROM video_channel WHERE deleted = 0"
                + (projectId == null ? "" : " AND project_id = " + projectId)));
        m.put("monitorOnline", count("SELECT COUNT(*) FROM video_channel WHERE deleted = 0 AND online = 1"
                + (projectId == null ? "" : " AND project_id = " + projectId)));
        m.put("batteryLow", count("SELECT COUNT(*) FROM device WHERE deleted = 0 AND battery IS NOT NULL AND battery < 20" + pf));
        m.put("readingToday", todayRows());
        m.put("updatedAt", LocalDateTime.now().toString());
        LocalCache.DASH.put(key, m);
        return m;
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0 : n;
    }

    /** 当日明细行数走分区元数据，不扫表。 */
    private long todayRows() {
        try {
            Long n = jdbc.queryForObject("""
                    SELECT SUM(p.rows) FROM sys.partitions p
                    JOIN sys.indexes i ON i.object_id = p.object_id AND i.index_id = p.index_id
                    WHERE i.object_id = OBJECT_ID('sensor_reading') AND p.index_id IN (0,1)
                      AND p.partition_number = $PARTITION.PF_DAY(CAST(SYSDATETIME() AS DATETIME2(3)))
                    """, Long.class);
            return n == null ? 0 : n;
        } catch (Exception e) {
            return 0;
        }
    }

    public List<Map<String, Object>> trend(int days) {
        List<Map<String, Object>> result = new ArrayList<>();
        List<Map<String, Object>> readings = jdbc.queryForList("""
                SELECT CAST(ts_hour AS DATE) AS day, SUM(sample_cnt) AS cnt
                FROM sensor_reading_hour
                WHERE ts_hour >= DATEADD(DAY, ?, CAST(SYSDATETIME() AS DATE))
                GROUP BY CAST(ts_hour AS DATE)
                ORDER BY day
                """, -days);
        List<Map<String, Object>> alarms = jdbc.queryForList("""
                SELECT CAST(first_ts AS DATE) AS day, COUNT(*) AS cnt
                FROM alarm
                WHERE first_ts >= DATEADD(DAY, ?, CAST(SYSDATETIME() AS DATE))
                GROUP BY CAST(first_ts AS DATE)
                ORDER BY day
                """, -days);
        for (Map<String, Object> r : readings) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", String.valueOf(r.get("day")));
            row.put("readingCount", r.get("cnt"));
            row.put("alarmCount", alarms.stream()
                    .filter(a -> String.valueOf(a.get("day")).equals(String.valueOf(r.get("day"))))
                    .map(a -> a.get("cnt")).findFirst().orElse(0));
            result.add(row);
        }
        return result;
    }

    public List<Map<String, Object>> topAlarm(int limit) {
        return jdbc.queryForList("""
                SELECT TOP (?) s.name AS sensorName, d.name AS deviceName, COUNT(*) AS cnt
                FROM alarm a
                JOIN sensor s ON s.id = a.sensor_id
                JOIN device d ON d.id = a.device_id
                GROUP BY s.name, d.name
                ORDER BY COUNT(*) DESC
                """, limit);
    }

    /** 大屏环形图：按传感器类型统计在册数量。 */
    public List<Map<String, Object>> typeDist(Long projectId) {
        return jdbc.queryForList("""
                SELECT t.name, COUNT(*) AS cnt
                FROM sensor s
                JOIN sensor_type t ON t.id = s.sensor_type_id
                JOIN device d ON d.id = s.device_id
                WHERE s.deleted = 0
                """ + (projectId == null ? "" : " AND d.project_id = " + projectId) + """
                GROUP BY t.name
                ORDER BY COUNT(*) DESC
                """);
    }

    /** 大屏实时数据流：最近 N 条入库记录，走 ts 倒序索引，只取一行。 */
    public List<Map<String, Object>> latestReadings(int limit) {
        return jdbc.queryForList("""
                SELECT TOP (?) s.name AS sensorName, f.name AS fieldName, f.unit,
                       r.value_num AS value, r.ts
                FROM sensor_reading r
                JOIN sensor s ON s.id = r.sensor_id
                JOIN sensor_field f ON f.id = r.field_id
                ORDER BY r.ts DESC
                """, limit);
    }
}

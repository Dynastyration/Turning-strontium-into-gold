package com.bngold.service;

import com.bngold.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class DataService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int[] STEPS = {1, 5, 15, 30, 60, 180, 360, 720, 1440};

    private final JdbcTemplate jdbc;

    public List<Map<String, Object>> fieldsOfType(Integer typeId) {
        return jdbc.queryForList("""
                SELECT id, field_key AS fieldKey, name, unit, decimals, is_primary AS isPrimary
                FROM sensor_field WHERE sensor_type_id = ? ORDER BY sort_no
                """, typeId);
    }

    /** 按传感器与字段取曲线，时间轴由实际数据桶组成。 */
    public Map<String, Object> series(Long sensorId, List<Integer> fieldIds,
                                      LocalDateTime from, LocalDateTime to, String interval) {
        int step = resolveStep(from, to, interval);
        String table = chooseTable(from, to);
        return buildSeries(List.of(sensorId), fieldIds, from, to, step, table);
    }

    public Map<String, Object> compare(List<Long> sensorIds, List<Integer> fieldIds,
                                       LocalDateTime from, LocalDateTime to, String interval) {
        if (sensorIds == null || sensorIds.isEmpty()) {
            throw BizException.badRequest("请选择对比的传感器");
        }
        int step = resolveStep(from, to, interval);
        String table = chooseTable(from, to);
        return buildSeries(sensorIds, fieldIds, from, to, step, table);
    }

    /**
     * 48 小时以内读明细，更长读小时聚合。
     * 分钟聚合表由定时任务维护，演示数据只有近两天的明细，因此这里不把查询压到分钟表上，
     * 否则 7 天以上的曲线会大面积空桶。真实项目把分钟表补满后，把中间档接回来即可。
     */
    private String chooseTable(LocalDateTime from, LocalDateTime to) {
        long hours = Duration.between(from, to).toHours();
        return hours <= 48 ? "sensor_reading" : "sensor_reading_hour";
    }

    private int resolveStep(LocalDateTime from, LocalDateTime to, String interval) {
        long spanMin = Math.max(1, Duration.between(from, to).toMinutes());
        int wanted;
        if (interval == null || interval.isBlank() || "auto".equalsIgnoreCase(interval)) {
            wanted = (int) Math.max(1, spanMin / 1000);
        } else {
            wanted = parseInterval(interval);
        }
        for (int s : STEPS) {
            if (s >= wanted) {
                return s;
            }
        }
        return 1440;
    }

    private int parseInterval(String interval) {
        String v = interval.trim().toLowerCase();
        try {
            if (v.endsWith("d")) {
                return Integer.parseInt(v.substring(0, v.length() - 1)) * 1440;
            }
            if (v.endsWith("h")) {
                return Integer.parseInt(v.substring(0, v.length() - 1)) * 60;
            }
            if (v.endsWith("m")) {
                return Integer.parseInt(v.substring(0, v.length() - 1));
            }
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 60;
        }
    }

    private Map<String, Object> buildSeries(List<Long> sensorIds, List<Integer> fieldIds,
                                            LocalDateTime from, LocalDateTime to,
                                            int step, String table) {
        String timeCol = switch (table) {
            case "sensor_reading" -> "ts";
            case "sensor_reading_min" -> "ts_min";
            default -> "ts_hour";
        };
        String valCol = "sensor_reading".equals(table) ? "value_num" : "val_avg";

        StringBuilder in = new StringBuilder();
        List<Object> args = new ArrayList<>();
        for (Long id : sensorIds) {
            in.append(in.isEmpty() ? "?" : ",?");
            args.add(id);
        }
        String fieldFilter = "";
        if (fieldIds != null && !fieldIds.isEmpty()) {
            StringBuilder fb = new StringBuilder();
            for (Integer f : fieldIds) {
                fb.append(fb.isEmpty() ? "?" : ",?");
                args.add(f);
            }
            fieldFilter = " AND field_id IN (" + fb + ") ";
        }
        args.add(from);
        args.add(to);

        // 步长必须以字面量进 SQL：占位符在 SELECT 与 GROUP BY 里算两个不同变量，
        // SQL Server 会判定表达式不等价而报「列 ts 未包含在聚合函数或 GROUP BY 子句中」。
        int stepMin = Math.max(1, step);
        String bucketExpr = "DATEADD(MINUTE, (DATEDIFF(MINUTE, 0, " + timeCol + ") / " + stepMin + ") * " + stepMin + ", 0)";
        String sql = "SELECT " + bucketExpr + " AS bucket, "
                + "sensor_id, field_id, AVG(" + valCol + ") AS v, MIN(" + valCol + ") AS vmin, MAX(" + valCol + ") AS vmax "
                + "FROM " + table
                + " WHERE sensor_id IN (" + in + ")" + fieldFilter
                + " AND " + timeCol + " >= ? AND " + timeCol + " < ? "
                + "GROUP BY " + bucketExpr + ", sensor_id, field_id "
                + "ORDER BY bucket";

        List<Object> ordered = new ArrayList<>(args);

        List<Map<String, Object>> rows = jdbc.queryForList(sql, ordered.toArray());

        TreeMap<String, Boolean> timeline = new TreeMap<>();
        Map<String, Map<String, Double>> values = new LinkedHashMap<>();
        Map<String, String> meta = new LinkedHashMap<>();

        for (Map<String, Object> r : rows) {
            String bucket = String.valueOf(r.get("bucket"));
            long sid = ((Number) r.get("sensor_id")).longValue();
            int fid = ((Number) r.get("field_id")).intValue();
            String key = sid + ":" + fid;
            timeline.put(bucket, Boolean.TRUE);
            values.computeIfAbsent(key, k -> new LinkedHashMap<>())
                    .put(bucket, ((Number) r.get("v")).doubleValue());
        }

        if (!rows.isEmpty()) {
            List<Object> keys = new ArrayList<>();
            StringBuilder cond = new StringBuilder();
            for (Map<String, Object> r : rows) {
                long sid = ((Number) r.get("sensor_id")).longValue();
                int fid = ((Number) r.get("field_id")).intValue();
                String k = sid + ":" + fid;
                if (!keys.contains(k)) {
                    keys.add(k);
                    cond.append(cond.isEmpty() ? "" : " OR ").append("(s.id = ? AND f.id = ?)");
                }
            }
            List<Object> metaArgs = new ArrayList<>();
            for (Object k : keys) {
                String[] parts = String.valueOf(k).split(":");
                metaArgs.add(Long.parseLong(parts[0]));
                metaArgs.add(Integer.parseInt(parts[1]));
            }
            List<Map<String, Object>> metas = jdbc.queryForList(
                    "SELECT CONCAT(CAST(s.id AS VARCHAR), ':', CAST(f.id AS VARCHAR)) AS k, "
                            + "s.name AS sensorName, f.field_key AS fieldKey, f.name AS fieldName, "
                            + "COALESCE(f.unit,'') AS unit FROM sensor s, sensor_field f "
                            + "WHERE s.deleted = 0 AND (" + cond + ")",
                    metaArgs.toArray());
            for (Map<String, Object> m : metas) {
                meta.put(String.valueOf(m.get("k")), m.get("sensorName") + "|" + m.get("fieldName")
                        + "|" + m.get("fieldKey") + "|" + m.get("unit"));
            }
        }

        List<String> axis = new ArrayList<>(timeline.keySet());
        List<Map<String, Object>> seriesList = new ArrayList<>();
        for (Map.Entry<String, Map<String, Double>> e : values.entrySet()) {
            String[] parts = e.getKey().split(":");
            String m = meta.getOrDefault(e.getKey(), "传感器" + parts[0] + "|字段" + parts[1] + "|" + parts[1] + "|");
            String[] mp = m.split("\\|", -1);
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("sensorId", Long.parseLong(parts[0]));
            s.put("fieldId", Integer.parseInt(parts[1]));
            s.put("sensorName", mp[0]);
            s.put("fieldName", mp.length > 1 ? mp[1] : "");
            s.put("fieldKey", mp.length > 2 ? mp[2] : "");
            s.put("unit", mp.length > 3 ? mp[3] : "");
            List<Double> vals = new ArrayList<>(axis.size());
            for (String t : axis) {
                Double v = e.getValue().get(t);
                vals.add(v == null ? null : Math.round(v * 100) / 100.0);
            }
            s.put("values", vals);
            s.put("labels", axis);
            seriesList.add(s);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("timeline", axis);
        result.put("series", seriesList);
        result.put("interval", step + "m");
        result.put("source", table);
        return result;
    }

    public Map<String, Object> readings(Long sensorId, LocalDateTime from, LocalDateTime to,
                                        long page, long size) {
        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sensor_reading WHERE sensor_id = ? AND ts >= ? AND ts < ?
                """, Long.class, sensorId, from, to);
        List<Map<String, Object>> list = jdbc.queryForList("""
                SELECT r.ts, f.name AS fieldName, f.field_key AS fieldKey, r.value_num AS value,
                       r.unit, r.received_at AS receivedAt
                FROM sensor_reading r JOIN sensor_field f ON f.id = r.field_id
                WHERE r.sensor_id = ? AND r.ts >= ? AND r.ts < ?
                ORDER BY r.ts DESC, f.sort_no
                OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
                """, sensorId, from, to, (page - 1) * size, size);
        return Map.of("list", list, "total", total == null ? 0 : total, "page", page, "size", size);
    }

    /** 多维查询：项目/设备/传感器/类型/字段/时间。 */
    public Map<String, Object> query(Long projectId, List<Long> deviceIds, List<Long> sensorIds,
                                     List<Integer> typeIds, List<String> fieldKeys,
                                     LocalDateTime from, LocalDateTime to, long page, long size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        if (projectId != null) {
            where.append(" AND d.project_id = ? ");
            args.add(projectId);
        }
        appendIn(where, args, "r.sensor_id", sensorIds);
        appendIn(where, args, "s.device_id", deviceIds);
        appendIn(where, args, "s.sensor_type_id", typeIds);
        if (fieldKeys != null && !fieldKeys.isEmpty()) {
            StringBuilder fb = new StringBuilder();
            for (String k : fieldKeys) {
                fb.append(fb.isEmpty() ? "?" : ",?");
                args.add(k);
            }
            where.append(" AND f.field_key IN (").append(fb).append(") ");
        }
        args.add(from);
        args.add(to);

        String fromSql = """
                FROM sensor_reading r
                JOIN sensor s ON s.id = r.sensor_id
                JOIN device d ON d.id = s.device_id
                JOIN sensor_field f ON f.id = r.field_id
                """;

        Long total = jdbc.queryForObject("SELECT COUNT(*) " + fromSql + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((page - 1) * size);
        pageArgs.add(size);
        List<Map<String, Object>> list = jdbc.queryForList("""
                SELECT r.ts, d.name AS deviceName, s.name AS sensorName, s.address,
                       f.name AS fieldName, f.field_key AS fieldKey, r.value_num AS value, r.unit
                """ + fromSql + where + " ORDER BY r.ts DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                pageArgs.toArray());
        return Map.of("list", list, "total", total == null ? 0 : total, "page", page, "size", size);
    }

    private void appendIn(StringBuilder where, List<Object> args, String col, List<?> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        StringBuilder b = new StringBuilder();
        for (Object v : values) {
            b.append(b.isEmpty() ? "?" : ",?");
            args.add(v);
        }
        where.append(" AND ").append(col).append(" IN (").append(b).append(") ");
    }

    public static LocalDateTime parse(String s, LocalDateTime def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        try {
            String t = s.trim().replace('T', ' ');
            if (t.length() >= 19) {
                return LocalDateTime.parse(t.substring(0, 19), FMT);
            }
            if (t.length() == 10) {
                return LocalDateTime.parse(t + " 00:00:00", FMT);
            }
            if (t.length() >= 16) {
                return LocalDateTime.parse(t.substring(0, 16) + ":00", FMT);
            }
        } catch (Exception ignored) {
            // 落到默认值
        }
        return def;
    }
}

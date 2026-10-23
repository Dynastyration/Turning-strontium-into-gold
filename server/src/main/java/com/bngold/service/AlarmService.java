package com.bngold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bngold.common.BizException;
import com.bngold.common.LocalCache;
import com.bngold.entity.Alarm;
import com.bngold.entity.AlarmConfig;
import com.bngold.entity.SensorField;
import com.bngold.mapper.AlarmConfigMapper;
import com.bngold.mapper.AlarmMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlarmService {

    private final AlarmMapper alarmMapper;
    private final AlarmConfigMapper configMapper;
    private final JdbcTemplate jdbc;

    private volatile List<AlarmConfig> cachedConfigs;
    private volatile long cacheAt;

    /** 规则缓存，60 秒过期；每条上报都查库会把接收端拖死。 */
    public List<AlarmConfig> configs() {
        if (cachedConfigs == null || System.currentTimeMillis() - cacheAt > 60_000) {
            cachedConfigs = configMapper.selectList(new LambdaQueryWrapper<AlarmConfig>()
                    .eq(AlarmConfig::getEnabled, 1));
            cacheAt = System.currentTimeMillis();
        }
        return cachedConfigs;
    }

    public void invalidate() {
        cachedConfigs = null;
    }

    /** 写入链路内同步判定：上限、下限、区间外。设备级规则由定时任务处理。 */
    public void evaluate(long sensorId, long deviceId, long projectId,
                         Map<String, SensorField> byKey, Map<String, Double> values, LocalDateTime ts) {
        if (values.isEmpty()) {
            return;
        }
        for (AlarmConfig c : configs()) {
            if (c.getFieldId() == null || c.getRuleType() == null) {
                continue;
            }
            if (c.getTargetType() == 5 && (c.getTargetId() == null || c.getTargetId() != sensorId)) {
                continue;
            }
            SensorField field = byKey.values().stream()
                    .filter(f -> f.getId().equals(c.getFieldId()))
                    .findFirst().orElse(null);
            if (field == null) {
                continue;
            }
            Double v = values.get(field.getFieldKey());
            if (v == null || !hit(c, v)) {
                continue;
            }
            raise(c, projectId, deviceId, sensorId, field, v, ts);
        }
    }

    private boolean hit(AlarmConfig c, double v) {
        Double t = c.getThreshold();
        return switch (c.getRuleType()) {
            case 1 -> t != null && v > t;
            case 2 -> t != null && v < t;
            case 3 -> t != null && c.getThreshold2() != null && (v > c.getThreshold2() || v < t);
            default -> false;
        };
    }

    private void raise(AlarmConfig c, long projectId, long deviceId, long sensorId,
                       SensorField field, double value, LocalDateTime ts) {
        String key = sensorId + ":" + c.getId();
        long now = System.currentTimeMillis();
        long[] open = LocalCache.ALARM_OPEN.getIfPresent(key);
        if (open != null && open[1] > now) {
            jdbc.update("UPDATE alarm SET occur_count = occur_count + 1, last_ts = ? WHERE id = ?", ts, open[0]);
            return;
        }
        String sensorName = jdbc.queryForObject("SELECT name FROM sensor WHERE id = ?", String.class, sensorId);
        String content = String.format("%s 的 %s 为 %.2f%s，%s %.2f%s",
                sensorName, field.getName(), value, nullToEmpty(field.getUnit()),
                ruleText(c.getRuleType()), c.getThreshold(), nullToEmpty(field.getUnit()));

        Alarm a = new Alarm();
        a.setConfigId(c.getId());
        a.setProjectId(projectId);
        a.setDeviceId(deviceId);
        a.setSensorId(sensorId);
        a.setFieldId(field.getId());
        a.setLevel(c.getLevel());
        a.setRuleType(c.getRuleType());
        a.setTriggerVal(value);
        a.setThreshold(c.getThreshold());
        a.setUnit(field.getUnit());
        a.setContent(content.length() > 250 ? content.substring(0, 250) : content);
        a.setStatus(0);
        a.setOccurCount(1);
        a.setFirstTs(ts);
        a.setLastTs(ts);
        alarmMapper.insert(a);

        int silence = c.getSilenceSec() == null ? 600 : c.getSilenceSec();
        LocalCache.ALARM_OPEN.put(key, new long[]{a.getId(), now + silence * 1000L});
        log.info("报警触发 sensor={} field={} value={} config={}", sensorId, field.getFieldKey(), value, c.getId());
    }

    private static String ruleText(int ruleType) {
        return switch (ruleType) {
            case 1 -> "超过上限";
            case 2 -> "低于下限";
            case 3 -> "超出区间";
            default -> "触发规则";
        };
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public Map<String, Object> page(Integer status, Integer level, String from, String to,
                                    Long sensorId, Long deviceId, Long projectId,
                                    long page, long size) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1 ");
        List<Object> args = new ArrayList<>();
        if (status != null) {
            where.append(" AND a.status = ? ");
            args.add(status);
        }
        if (level != null) {
            where.append(" AND a.level = ? ");
            args.add(level);
        }
        if (from != null && !from.isBlank()) {
            where.append(" AND a.last_ts >= ? ");
            args.add(from);
        }
        if (to != null && !to.isBlank()) {
            where.append(" AND a.last_ts <= ? ");
            args.add(to);
        }
        if (sensorId != null) {
            where.append(" AND a.sensor_id = ? ");
            args.add(sensorId);
        }
        if (deviceId != null) {
            where.append(" AND a.device_id = ? ");
            args.add(deviceId);
        }
        if (projectId != null) {
            where.append(" AND a.project_id = ? ");
            args.add(projectId);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM alarm a" + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((page - 1) * size);
        pageArgs.add(size);
        List<Map<String, Object>> list = jdbc.queryForList("""
                SELECT a.id, a.level, a.rule_type AS ruleType, a.trigger_val AS triggerVal,
                       a.threshold, a.unit, a.content, a.status, a.occur_count AS occurCount,
                       a.first_ts AS firstTs, a.last_ts AS lastTs, a.ack_at AS ackAt,
                       a.sensor_id AS sensorId, s.name AS sensorName,
                       d.name AS deviceName, p.name AS projectName, f.name AS fieldName
                FROM alarm a
                LEFT JOIN sensor s ON s.id = a.sensor_id
                LEFT JOIN device d ON d.id = a.device_id
                LEFT JOIN project p ON p.id = a.project_id
                LEFT JOIN sensor_field f ON f.id = a.field_id
                """ + where + " ORDER BY a.last_ts DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                pageArgs.toArray());

        return Map.of("list", list, "total", total == null ? 0 : total, "page", page, "size", size);
    }

    public void ack(Long id, Long userId) {
        Alarm a = alarmMapper.selectById(id);
        if (a == null) {
            throw BizException.notFound("报警");
        }
        if (a.getAckAt() != null) {
            return;
        }
        Alarm patch = new Alarm();
        patch.setId(id);
        patch.setStatus(1);
        patch.setAckBy(userId);
        patch.setAckAt(LocalDateTime.now());
        alarmMapper.updateById(patch);
    }

    public void close(Long id) {
        Alarm a = alarmMapper.selectById(id);
        if (a == null) {
            throw BizException.notFound("报警");
        }
        Alarm patch = new Alarm();
        patch.setId(id);
        patch.setStatus(3);
        patch.setCloseAt(LocalDateTime.now());
        alarmMapper.updateById(patch);
    }

    // 侧栏红点只数「待确认」：用户确认过就不该再亮，关闭自然也不算
    public long unreadCount() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM alarm WHERE status = 0", Long.class);
        return n == null ? 0 : n;
    }

    public List<Map<String, Object>> statByLevel() {
        return jdbc.queryForList("""
                SELECT level, COUNT(*) AS cnt FROM alarm WHERE status IN (0, 1)
                GROUP BY level ORDER BY level
                """);
    }

    public List<Map<String, Object>> statByDay(int days) {
        return jdbc.queryForList("""
                SELECT CAST(first_ts AS DATE) AS day, COUNT(*) AS cnt
                FROM alarm WHERE first_ts >= DATEADD(DAY, ?, CAST(SYSDATETIME() AS DATE))
                GROUP BY CAST(first_ts AS DATE) ORDER BY day
                """, -days);
    }

    public List<AlarmConfig> listConfigs() {
        return configMapper.selectList(new LambdaQueryWrapper<AlarmConfig>().orderByAsc(AlarmConfig::getId));
    }

    public void saveConfig(AlarmConfig c) {
        if (c.getId() == null) {
            c.setCreatedAt(LocalDateTime.now());
            configMapper.insert(c);
        } else {
            configMapper.updateById(c);
        }
        invalidate();
    }

    public void deleteConfig(Long id) {
        configMapper.deleteById(id);
        invalidate();
    }
}

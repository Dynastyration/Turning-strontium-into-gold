package com.bngold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bngold.common.BizException;
import com.bngold.entity.Sensor;
import com.bngold.entity.SensorGroup;
import com.bngold.mapper.SensorGroupMapper;
import com.bngold.mapper.SensorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SensorService {

    private final SensorMapper sensorMapper;
    private final SensorGroupMapper groupMapper;
    private final JdbcTemplate jdbc;

    public Map<String, Object> page(String keyword, String name, Integer typeId, Long deviceId,
                                    Long groupId, Integer status, long page, long size) {
        StringBuilder where = new StringBuilder(" WHERE s.deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (s.name LIKE ? OR CAST(s.address AS NVARCHAR(16)) = ? OR d.device_num LIKE ?) ");
            args.add("%" + keyword + "%");
            args.add(keyword);
            args.add("%" + keyword + "%");
        }
        if (name != null && !name.isBlank()) {
            where.append(" AND s.name LIKE ? ");
            args.add("%" + name + "%");
        }
        if (typeId != null) {
            where.append(" AND s.sensor_type_id = ? ");
            args.add(typeId);
        }
        if (deviceId != null) {
            where.append(" AND s.device_id = ? ");
            args.add(deviceId);
        }
        if (groupId != null) {
            where.append(" AND s.group_id = ? ");
            args.add(groupId);
        }
        if (status != null) {
            where.append(" AND s.status = ? ");
            args.add(status);
        }

        Long total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM sensor s JOIN device d ON d.id = s.device_id
                """ + where, Long.class, args.toArray());

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((page - 1) * size);
        pageArgs.add(size);

        List<Map<String, Object>> list = jdbc.queryForList("""
                SELECT s.id, s.name, s.address, s.status, s.device_id AS deviceId, d.name AS deviceName,
                       d.device_num AS deviceNum, s.group_id AS groupId, g.name AS groupName,
                       s.sensor_type_id AS sensorTypeId, t.name AS sensorTypeName,
                       s.install_position AS installPosition, s.last_value AS lastValue,
                       s.last_ts AS lastTs, s.created_at AS createdAt, s.alarm_enabled AS alarmEnabled
                FROM sensor s
                JOIN device d ON d.id = s.device_id
                LEFT JOIN sensor_group g ON g.id = s.group_id
                LEFT JOIN sensor_type t ON t.id = s.sensor_type_id
                """ + where + " ORDER BY s.device_id, s.address OFFSET ? ROWS FETCH NEXT ? ROWS ONLY",
                pageArgs.toArray());

        return Map.of("list", list, "total", total == null ? 0 : total, "page", page, "size", size);
    }

    public Map<String, Object> detail(Long id) {
        Sensor s = sensorMapper.selectById(id);
        if (s == null || Integer.valueOf(1).equals(s.getDeleted())) {
            throw BizException.notFound("传感器");
        }
        List<Map<String, Object>> fields = jdbc.queryForList("""
                SELECT id, field_key AS fieldKey, name, unit, decimals, is_primary AS isPrimary
                FROM sensor_field WHERE sensor_type_id = ? ORDER BY sort_no
                """, s.getSensorTypeId());
        return Map.of("sensor", s, "fields", fields, "latest", latest(id));
    }

    public void update(Long id, Sensor patch) {
        Sensor exists = sensorMapper.selectById(id);
        if (exists == null) {
            throw BizException.notFound("传感器");
        }
        patch.setId(id);
        if (patch.getName() != null) {
            patch.setNameIsManual(1);
        }
        patch.setUpdatedAt(LocalDateTime.now());
        sensorMapper.updateById(patch);
    }

    /** 每个字段取最新一条，CROSS APPLY 一次走完，不做 N+1。 */
    public List<Map<String, Object>> latest(Long sensorId) {
        return jdbc.queryForList("""
                SELECT f.field_key AS fieldKey, f.name, f.unit, f.decimals,
                       r.value_num AS value, r.value_str AS valueStr, r.ts
                FROM sensor_field f
                CROSS APPLY (
                    SELECT TOP 1 value_num, value_str, ts
                    FROM sensor_reading
                    WHERE sensor_id = ? AND field_id = f.id
                    ORDER BY ts DESC
                ) r
                WHERE f.sensor_type_id = (SELECT sensor_type_id FROM sensor WHERE id = ?)
                ORDER BY f.sort_no
                """, sensorId, sensorId);
    }

    public List<SensorGroup> groups(Long projectId) {
        return groupMapper.selectList(new LambdaQueryWrapper<SensorGroup>()
                .eq(SensorGroup::getDeleted, 0)
                .eq(projectId != null, SensorGroup::getProjectId, projectId)
                .orderByAsc(SensorGroup::getSortNo));
    }

    public Long saveGroup(SensorGroup g) {
        g.setDeleted(0);
        g.setCreatedAt(LocalDateTime.now());
        groupMapper.insert(g);
        return g.getId();
    }

    public void updateGroup(Long id, SensorGroup g) {
        g.setId(id);
        groupMapper.updateById(g);
    }

    public void deleteGroup(Long id) {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM sensor WHERE group_id = ? AND deleted = 0",
                Long.class, id);
        if (n != null && n > 0) {
            throw new BizException(30003, "该分组下还有 " + n + " 个传感器");
        }
        SensorGroup g = new SensorGroup();
        g.setId(id);
        g.setDeleted(1);
        groupMapper.updateById(g);
    }

    public void assignGroup(List<Long> ids, Long groupId) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            Sensor s = new Sensor();
            s.setId(id);
            s.setGroupId(groupId);
            s.setUpdatedAt(LocalDateTime.now());
            sensorMapper.updateById(s);
        }
    }
}

package com.bngold.service;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bngold.common.LocalCache;
import com.bngold.entity.Device;
import com.bngold.entity.Project;
import com.bngold.entity.Sensor;
import com.bngold.entity.SensorField;
import com.bngold.entity.SensorType;
import com.bngold.mapper.DeviceMapper;
import com.bngold.mapper.ProjectMapper;
import com.bngold.mapper.SensorFieldMapper;
import com.bngold.mapper.SensorMapper;
import com.bngold.mapper.SensorTypeMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BN 云平台推送接收：动态字段解析、幂等、元数据 UPSERT、明细入队、报警判定。
 * 字段名来自 sensortype.sensorfields[].field，不能写死成列。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FMT_MIN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProjectMapper projectMapper;
    private final DeviceMapper deviceMapper;
    private final SensorTypeMapper sensorTypeMapper;
    private final SensorFieldMapper sensorFieldMapper;
    private final SensorMapper sensorMapper;
    private final ReadingWriter readingWriter;
    private final AlarmService alarmService;
    private final JdbcTemplate jdbc;

    /** payload 单条对象或数组，统一拆成单条处理 */
    public int acceptPayload(JsonNode payload) {
        if (payload.isArray()) {
            int n = 0;
            for (JsonNode node : payload) {
                if (acceptOne(node)) {
                    n++;
                }
            }
            return n;
        }
        return acceptOne(payload) ? 1 : 0;
    }

    private boolean acceptOne(JsonNode payload) {
        JsonNode data = payload.path("sensordata");
        JsonNode info = payload.path("sensorinfo");
        if (data.isMissingNode() || info.isMissingNode()) {
            log.warn("报文缺少 sensordata/sensorinfo，丢弃: {}", shorten(payload.toString()));
            return false;
        }

        int sensorSourceId = data.path("sensor_id").asInt(0);
        LocalDateTime ts = parseTime(data.path("created_at").asText(null));
        String md5 = SecureUtil.md5(payload.toString());

        String idemKey = "bn:idem:" + sensorSourceId + ":" + ts + ":" + md5;
        if (LocalCache.IDEM.getIfPresent(idemKey) != null) {
            return false;
        }
        LocalCache.IDEM.put(idemKey, Boolean.TRUE);

        long projectId = upsertProject(info.path("project"));
        long deviceId = upsertDevice(info.path("device"), projectId);
        int typeId = upsertSensorType(payload.path("sensortype"));
        List<SensorField> fields = upsertFields(typeId, payload.path("sensortype").path("sensorfields"));
        long sensorId = upsertSensor(info, deviceId, typeId, sensorSourceId);

        jdbc.update("""
                INSERT INTO sensor_reading_raw (sensor_id, ts, payload, payload_md5, parsed)
                VALUES (?, ?, ?, ?, ?)
                """, sensorId, ts, payload.toString(), md5, fields.isEmpty() ? 0 : 1);

        Map<String, SensorField> byKey = new LinkedHashMap<>();
        Map<String, Double> values = new HashMap<>();
        for (SensorField f : fields) {
            byKey.put(f.getFieldKey(), f);
            JsonNode v = data.get(f.getFieldKey());
            if (v == null || v.isNull()) {
                continue;
            }
            Double num = toDouble(v);
            if (num == null) {
                readingWriter.submit(new ReadingWriter.Row(sensorId, f.getId(), ts, null, v.asText(), f.getUnit()));
            } else {
                readingWriter.submit(new ReadingWriter.Row(sensorId, f.getId(), ts, num, null, f.getUnit()));
                values.put(f.getFieldKey(), num);
            }
        }

        Map<String, Object> latest = new HashMap<>(values);
        latest.put("ts", ts.toString());
        LocalCache.LATEST.put(sensorId, latest);

        SensorField primary = fields.stream()
                .filter(f -> Integer.valueOf(1).equals(f.getIsPrimary()))
                .findFirst()
                .orElse(fields.isEmpty() ? null : fields.get(0));
        if (primary != null && values.containsKey(primary.getFieldKey())) {
            jdbc.update("UPDATE sensor SET last_value = ?, last_ts = ? WHERE id = ?",
                    values.get(primary.getFieldKey()), ts, sensorId);
        }
        jdbc.update("UPDATE device SET last_push_at = ?, run_status = 1 WHERE id = ?", ts, deviceId);

        alarmService.evaluate(sensorId, deviceId, projectId, byKey, values, ts);
        return true;
    }

    private long upsertProject(JsonNode p) {
        Integer src = p.path("id").asInt(0);
        if (src == 0) {
            src = null;
        }
        Project exists = src == null ? null
                : projectMapper.selectOne(new LambdaQueryWrapper<Project>().eq(Project::getSourceId, src));
        if (exists != null) {
            return exists.getId();
        }
        Project np = new Project();
        np.setSourceId(src);
        np.setName(text(p, "name", "未命名项目"));
        np.setArea(text(p, "area", null));
        np.setAddress(text(p, "address", null));
        np.setSortNo(0);
        np.setDeleted(0);
        np.setCreatedAt(LocalDateTime.now());
        np.setUpdatedAt(LocalDateTime.now());
        projectMapper.insert(np);
        return np.getId();
    }

    private long upsertDevice(JsonNode d, long projectId) {
        String deviceNum = text(d, "device_num", null);
        Device exists = deviceNum == null || deviceNum.isBlank() ? null
                : deviceMapper.selectOne(new LambdaQueryWrapper<Device>().eq(Device::getDeviceNum, deviceNum));
        if (exists != null) {
            int src = d.path("id").asInt(0);
            if (exists.getSourceId() == null && src > 0) {
                Device patch = new Device();
                patch.setId(exists.getId());
                patch.setSourceId(src);
                patch.setProjectId(projectId);
                deviceMapper.updateById(patch);
            }
            return exists.getId();
        }
        Device nd = new Device();
        nd.setSourceId(d.path("id").asInt(0) == 0 ? null : d.path("id").asInt());
        nd.setDeviceNum(deviceNum == null ? ("AUTO-" + System.currentTimeMillis()) : deviceNum);
        nd.setName(text(d, "name", nd.getDeviceNum()));
        nd.setTypeId(1);
        nd.setProjectId(projectId);
        nd.setRunStatus(1);
        nd.setSensorCount(0);
        nd.setCollectPeriod(300);
        nd.setCmdInterval(1);
        nd.setDebugMode(0);
        nd.setCfgStatus(0);
        nd.setPowerType(1);
        nd.setSortNo(0);
        nd.setMapLevel(5);
        nd.setDeleted(0);
        nd.setCreatedAt(LocalDateTime.now());
        nd.setUpdatedAt(LocalDateTime.now());
        deviceMapper.insert(nd);
        return nd.getId();
    }

    private int upsertSensorType(JsonNode t) {
        int src = t.path("id").asInt(0);
        String name = text(t, "name", null);
        SensorType exists = src > 0
                ? sensorTypeMapper.selectOne(new LambdaQueryWrapper<SensorType>().eq(SensorType::getSourceId, src))
                : null;
        if (exists == null && name != null) {
            exists = sensorTypeMapper.selectOne(new LambdaQueryWrapper<SensorType>().eq(SensorType::getName, name));
        }
        if (exists != null) {
            return exists.getId();
        }
        SensorType nt = new SensorType();
        nt.setSourceId(src == 0 ? null : src);
        nt.setName(name == null ? "未分类" : name);
        nt.setDeleted(0);
        nt.setCreatedAt(LocalDateTime.now());
        sensorTypeMapper.insert(nt);
        return nt.getId();
    }

    private List<SensorField> upsertFields(int typeId, JsonNode sensorFields) {
        List<SensorField> result = new ArrayList<>();
        if (!sensorFields.isArray()) {
            return result;
        }
        for (JsonNode f : sensorFields) {
            String key = text(f, "field", null);
            if (key == null || key.isBlank()) {
                continue;
            }
            int src = f.path("id").asInt(0);
            SensorField exists = sensorFieldMapper.selectOne(new LambdaQueryWrapper<SensorField>()
                    .eq(SensorField::getSensorTypeId, typeId)
                    .eq(SensorField::getFieldKey, key));
            if (exists == null) {
                SensorField nf = new SensorField();
                nf.setSensorTypeId(typeId);
                nf.setSourceId(src == 0 ? null : src);
                nf.setFieldKey(key);
                nf.setName(text(f, "name", key));
                nf.setUnit(text(f, "unit", null));
                nf.setValueType(1);
                nf.setDecimals(2);
                nf.setIsPrimary(0);
                nf.setSortNo(result.size());
                nf.setCreatedAt(LocalDateTime.now());
                sensorFieldMapper.insert(nf);
                exists = nf;
            }
            result.add(exists);
        }
        return result;
    }

    private long upsertSensor(JsonNode info, long deviceId, int typeId, int sensorSourceId) {
        String name = text(info, "name", "未命名传感器");
        int address = info.path("address").asInt(0);
        Sensor exists = sensorMapper.selectOne(new LambdaQueryWrapper<Sensor>()
                .eq(Sensor::getDeviceId, deviceId)
                .eq(Sensor::getAddress, address));
        if (exists != null) {
            if (Integer.valueOf(0).equals(exists.getNameIsManual()) && !name.equals(exists.getName())) {
                Sensor patch = new Sensor();
                patch.setId(exists.getId());
                patch.setName(name);
                patch.setSensorTypeId(typeId);
                patch.setUpdatedAt(LocalDateTime.now());
                sensorMapper.updateById(patch);
            }
            return exists.getId();
        }
        Sensor ns = new Sensor();
        ns.setSourceId(sensorSourceId == 0 ? null : sensorSourceId);
        ns.setAddress(address);
        ns.setName(name);
        ns.setNameIsManual(0);
        ns.setDeviceId(deviceId);
        ns.setSensorTypeId(typeId);
        ns.setStatus(1);
        ns.setAlarmEnabled(1);
        ns.setDeleted(0);
        ns.setCreatedAt(LocalDateTime.now());
        ns.setUpdatedAt(LocalDateTime.now());
        sensorMapper.insert(ns);
        jdbc.update("UPDATE device SET sensor_count = sensor_count + 1 WHERE id = ?", deviceId);
        return ns.getId();
    }

    private static String text(JsonNode node, String field, String def) {
        JsonNode v = node.path(field);
        return v.isMissingNode() || v.isNull() || v.asText().isBlank() ? def : v.asText();
    }

    private static Double toDouble(JsonNode v) {
        if (v.isNumber()) {
            return v.asDouble();
        }
        try {
            return Double.parseDouble(v.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDateTime parseTime(String s) {
        if (s == null || s.isBlank()) {
            return LocalDateTime.now();
        }
        String t = s.trim().replace('T', ' ');
        try {
            if (t.length() >= 19) {
                return LocalDateTime.parse(t.substring(0, 19), FMT);
            }
            if (t.length() >= 16) {
                return LocalDateTime.parse(t.substring(0, 16), FMT_MIN);
            }
        } catch (Exception e) {
            log.warn("时间解析失败: {}", s);
        }
        return LocalDateTime.now();
    }

    private static String shorten(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}

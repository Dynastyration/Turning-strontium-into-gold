package com.bngold.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bngold.common.BizException;
import com.bngold.common.PageResult;
import com.bngold.entity.Device;
import com.bngold.entity.DeviceCommand;
import com.bngold.mapper.DeviceCommandMapper;
import com.bngold.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceMapper deviceMapper;
    private final DeviceCommandMapper commandMapper;
    private final JdbcTemplate jdbc;

    public PageResult<Device> page(String keyword, Integer typeId, Integer status, Integer powerType,
                                   Long projectId, long page, long size) {
        LambdaQueryWrapper<Device> w = new LambdaQueryWrapper<Device>()
                .eq(Device::getDeleted, 0)
                .eq(typeId != null, Device::getTypeId, typeId)
                .eq(status != null, Device::getRunStatus, status)
                .eq(powerType != null, Device::getPowerType, powerType)
                .eq(projectId != null, Device::getProjectId, projectId)
                .and(keyword != null && !keyword.isBlank(), q -> q
                        .like(Device::getName, keyword)
                        .or().like(Device::getDeviceNum, keyword))
                .orderByAsc(Device::getSortNo)
                .orderByDesc(Device::getId);
        return PageResult.of(deviceMapper.selectPage(new Page<>(page, size), w));
    }

    public Device detail(Long id) {
        Device d = deviceMapper.selectById(id);
        if (d == null || Integer.valueOf(1).equals(d.getDeleted())) {
            throw BizException.notFound("设备");
        }
        return d;
    }

    public Long save(Device d) {
        d.setDeleted(0);
        d.setSensorCount(0);
        d.setCreatedAt(LocalDateTime.now());
        d.setUpdatedAt(LocalDateTime.now());
        if (d.getRunStatus() == null) {
            d.setRunStatus(0);
        }
        if (d.getCollectPeriod() == null) {
            d.setCollectPeriod(300);
        }
        if (d.getCmdInterval() == null) {
            d.setCmdInterval(1);
        }
        deviceMapper.insert(d);
        return d.getId();
    }

    public void update(Long id, Device d) {
        detail(id);
        d.setId(id);
        d.setUpdatedAt(LocalDateTime.now());
        deviceMapper.updateById(d);
    }

    @Transactional
    public void remove(Long id) {
        detail(id);
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM sensor WHERE device_id = ? AND deleted = 0",
                Long.class, id);
        if (n != null && n > 0) {
            throw new BizException(30002, "该设备下还有 " + n + " 个传感器，请先处理");
        }
        Device patch = new Device();
        patch.setId(id);
        patch.setDeleted(1);
        patch.setUpdatedAt(LocalDateTime.now());
        deviceMapper.updateById(patch);
    }

    public List<Map<String, Object>> geo(Long projectId) {
        String sql = """
                SELECT id, device_num AS deviceNum, name, run_status AS runStatus,
                       lng, lat, sensor_count AS sensorCount, battery
                FROM device WHERE deleted = 0 AND lng IS NOT NULL
                """ + (projectId == null ? "" : " AND project_id = " + projectId);
        return jdbc.queryForList(sql);
    }

    public Map<String, Object> sensorsOf(Long deviceId) {
        Device d = detail(deviceId);
        List<Map<String, Object>> list = jdbc.queryForList("""
                SELECT s.id, s.name, s.address, s.status, s.group_id AS groupId,
                       t.name AS sensorTypeName, s.last_value AS lastValue, s.last_ts AS lastTs
                FROM sensor s LEFT JOIN sensor_type t ON t.id = s.sensor_type_id
                WHERE s.device_id = ? AND s.deleted = 0
                ORDER BY s.address
                """, deviceId);
        return Map.of("deviceId", d.getId(), "deviceNum", d.getDeviceNum(),
                "deviceName", d.getName(), "list", list);
    }

    /** 远程配置下发：落指令表，等待设备回执。 */
    public Long command(Long deviceId, String cmdType, String payload) {
        Device d = detail(deviceId);
        DeviceCommand c = new DeviceCommand();
        c.setDeviceId(deviceId);
        c.setCmdType(cmdType);
        c.setPayload(payload == null || payload.isBlank() ? "{}" : payload);
        c.setStatus(1);
        c.setIssuedAt(LocalDateTime.now());
        c.setRetryCount(0);
        c.setCreatedAt(LocalDateTime.now());
        commandMapper.insert(c);

        Device patch = new Device();
        patch.setId(deviceId);
        patch.setCfgStatus(1);
        patch.setUpdatedAt(LocalDateTime.now());
        deviceMapper.updateById(patch);
        return c.getId();
    }

    public DeviceCommand commandStatus(Long commandId) {
        return commandMapper.selectById(commandId);
    }
}

package com.bngold.controller;

import com.bngold.common.R;
import com.bngold.config.AuthContext;
import com.bngold.entity.AlarmConfig;
import com.bngold.service.AlarmService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alarmService;

    @GetMapping("/alarms")
    public R<Map<String, Object>> page(@RequestParam(required = false) Integer status,
                                       @RequestParam(required = false) Integer level,
                                       @RequestParam(required = false) String from,
                                       @RequestParam(required = false) String to,
                                       @RequestParam(required = false) Long sensorId,
                                       @RequestParam(required = false) Long deviceId,
                                       @RequestParam(required = false) Long projectId,
                                       @RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "20") long size) {
        return R.ok(alarmService.page(status, level, from, to, sensorId, deviceId, projectId, page, size));
    }

    @PostMapping("/alarms/{id}/ack")
    public R<Void> ack(@PathVariable Long id) {
        alarmService.ack(id, AuthContext.userId());
        return R.ok();
    }

    @PostMapping("/alarms/{id}/close")
    public R<Void> close(@PathVariable Long id) {
        alarmService.close(id);
        return R.ok();
    }

    @GetMapping("/alarms/stat")
    public R<?> stat(@RequestParam(defaultValue = "level") String groupBy) {
        if ("day".equalsIgnoreCase(groupBy)) {
            return R.ok(alarmService.statByDay(7));
        }
        return R.ok(alarmService.statByLevel());
    }

    @GetMapping("/alarm-configs")
    public R<List<AlarmConfig>> configs() {
        return R.ok(alarmService.listConfigs());
    }

    @PostMapping("/alarm-configs")
    public R<Long> createConfig(@RequestBody AlarmConfig config) {
        alarmService.saveConfig(config);
        return R.ok(config.getId());
    }

    @PutMapping("/alarm-configs/{id}")
    public R<Void> updateConfig(@PathVariable Long id, @RequestBody AlarmConfig config) {
        config.setId(id);
        alarmService.saveConfig(config);
        return R.ok();
    }

    @DeleteMapping("/alarm-configs/{id}")
    public R<Void> deleteConfig(@PathVariable Long id) {
        alarmService.deleteConfig(id);
        return R.ok();
    }
}

package com.bngold.controller;

import com.bngold.common.R;
import com.bngold.entity.Sensor;
import com.bngold.entity.SensorGroup;
import com.bngold.service.DataService;
import com.bngold.service.SensorService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/sensors")
@RequiredArgsConstructor
public class SensorController {

    private final SensorService sensorService;
    private final DataService dataService;

    @GetMapping
    public R<Map<String, Object>> page(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String name,
                                       @RequestParam(required = false) Integer typeId,
                                       @RequestParam(required = false) Long deviceId,
                                       @RequestParam(required = false) Long groupId,
                                       @RequestParam(required = false) Integer status,
                                       @RequestParam(defaultValue = "1") long page,
                                       @RequestParam(defaultValue = "12") long size) {
        return R.ok(sensorService.page(keyword, name, typeId, deviceId, groupId, status, page, size));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(sensorService.detail(id));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Sensor sensor) {
        sensorService.update(id, sensor);
        return R.ok();
    }

    @GetMapping("/{id}/latest")
    public R<List<Map<String, Object>>> latest(@PathVariable Long id) {
        return R.ok(sensorService.latest(id));
    }

    @GetMapping("/{id}/series")
    public R<Map<String, Object>> series(@PathVariable Long id,
                                         @RequestParam(required = false) String fields,
                                         @RequestParam(required = false) String from,
                                         @RequestParam(required = false) String to,
                                         @RequestParam(defaultValue = "auto") String interval) {
        LocalDateTime end = DataService.parse(to, LocalDateTime.now());
        LocalDateTime start = DataService.parse(from, end.minusDays(1));
        List<Integer> fieldIds = parseIds(fields);
        return R.ok(dataService.series(id, fieldIds, start, end, interval));
    }

    @GetMapping("/{id}/readings")
    public R<Map<String, Object>> readings(@PathVariable Long id,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to,
                                           @RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "50") long size) {
        LocalDateTime end = DataService.parse(to, LocalDateTime.now());
        LocalDateTime start = DataService.parse(from, end.minusHours(2));
        return R.ok(dataService.readings(id, start, end, page, size));
    }

    @GetMapping("/groups")
    public R<List<SensorGroup>> groups(@RequestParam(required = false) Long projectId) {
        return R.ok(sensorService.groups(projectId));
    }

    @PostMapping("/groups")
    public R<Long> createGroup(@RequestBody SensorGroup group) {
        return R.ok(sensorService.saveGroup(group));
    }

    @PutMapping("/groups/{id}")
    public R<Void> updateGroup(@PathVariable Long id, @RequestBody SensorGroup group) {
        sensorService.updateGroup(id, group);
        return R.ok();
    }

    @DeleteMapping("/groups/{id}")
    public R<Void> deleteGroup(@PathVariable Long id) {
        sensorService.deleteGroup(id);
        return R.ok();
    }

    public record AssignReq(List<Long> ids, Long groupId) {
    }

    @PutMapping("/group")
    public R<Void> assign(@RequestBody AssignReq req) {
        sensorService.assignGroup(req.ids(), req.groupId());
        return R.ok();
    }

    private static List<Integer> parseIds(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .toList();
    }
}

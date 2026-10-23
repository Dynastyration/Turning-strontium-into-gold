package com.bngold.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bngold.common.R;
import com.bngold.entity.Project;
import com.bngold.entity.SensorField;
import com.bngold.entity.SensorType;
import com.bngold.entity.SysDict;
import com.bngold.mapper.ProjectMapper;
import com.bngold.mapper.SensorFieldMapper;
import com.bngold.mapper.SensorTypeMapper;
import com.bngold.mapper.SysDictMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MetaController {

    private final SysDictMapper dictMapper;
    private final ProjectMapper projectMapper;
    private final SensorTypeMapper sensorTypeMapper;
    private final SensorFieldMapper sensorFieldMapper;

    @GetMapping("/dicts")
    public R<List<SysDict>> dicts(@RequestParam String type) {
        return R.ok(dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictType, type)
                .orderByAsc(SysDict::getSortNo)));
    }

    @GetMapping("/dicts/all")
    public R<Map<String, List<SysDict>>> allDicts() {
        Map<String, List<SysDict>> grouped = new LinkedHashMap<>();
        for (SysDict d : dictMapper.selectList(new LambdaQueryWrapper<SysDict>().orderByAsc(SysDict::getSortNo))) {
            grouped.computeIfAbsent(d.getDictType(), k -> new java.util.ArrayList<>()).add(d);
        }
        return R.ok(grouped);
    }

    @GetMapping("/projects")
    public R<List<Project>> projects() {
        return R.ok(projectMapper.selectList(new LambdaQueryWrapper<Project>()
                .eq(Project::getDeleted, 0)
                .orderByAsc(Project::getSortNo)));
    }

    @GetMapping("/sensor-types")
    public R<List<Map<String, Object>>> sensorTypes() {
        List<SensorType> types = sensorTypeMapper.selectList(new LambdaQueryWrapper<SensorType>()
                .eq(SensorType::getDeleted, 0));
        return R.ok(types.stream().map(t -> {
            List<SensorField> fields = sensorFieldMapper.selectList(new LambdaQueryWrapper<SensorField>()
                    .eq(SensorField::getSensorTypeId, t.getId())
                    .orderByAsc(SensorField::getSortNo));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("name", t.getName());
            m.put("category", t.getCategory());
            m.put("fields", fields);
            return m;
        }).toList());
    }
}

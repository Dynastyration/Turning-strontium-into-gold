package com.bngold.controller;

import com.bngold.common.R;
import com.bngold.config.AuthContext;
import com.bngold.service.DataService;
import com.bngold.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DataController {

    private final DataService dataService;
    private final ReportService reportService;

    public record QueryReq(Long projectId, List<Long> deviceIds, List<Long> sensorIds,
                           List<Integer> typeIds, List<String> fieldKeys,
                           String from, String to, Long page, Long size) {
    }

    @PostMapping("/data/query")
    public R<Map<String, Object>> query(@RequestBody QueryReq req) {
        LocalDateTime end = DataService.parse(req.to(), LocalDateTime.now());
        LocalDateTime start = DataService.parse(req.from(), end.minusHours(6));
        long page = req.page() == null ? 1 : req.page();
        long size = req.size() == null ? 50 : Math.min(req.size(), 500);
        return R.ok(dataService.query(req.projectId(), req.deviceIds(), req.sensorIds(),
                req.typeIds(), req.fieldKeys(), start, end, page, size));
    }

    public record CompareReq(List<Long> sensorIds, List<Integer> fieldIds,
                             String from, String to, String interval) {
    }

    @PostMapping("/data/compare")
    public R<Map<String, Object>> compare(@RequestBody CompareReq req) {
        LocalDateTime end = DataService.parse(req.to(), LocalDateTime.now());
        LocalDateTime start = DataService.parse(req.from(), end.minusDays(1));
        return R.ok(dataService.compare(req.sensorIds(), req.fieldIds(), start, end, req.interval()));
    }

    @GetMapping("/data/fields")
    public R<List<Map<String, Object>>> fields(@RequestParam Integer typeId) {
        return R.ok(dataService.fieldsOfType(typeId));
    }

    public record ReportReq(Integer bizType, String params, Integer fmt) {
    }

    @PostMapping("/reports")
    public R<Map<String, Object>> createReport(@RequestBody ReportReq req) {
        Long id = reportService.create(
                req.bizType() == null ? 1 : req.bizType(),
                req.params(),
                req.fmt() == null ? 1 : req.fmt(),
                AuthContext.userId());
        return R.ok(Map.of("taskId", id));
    }

    @GetMapping("/reports")
    public R<List<Map<String, Object>>> reports() {
        return R.ok(reportService.list(AuthContext.userId()));
    }

    @GetMapping("/reports/{id}")
    public R<Map<String, Object>> report(@PathVariable Long id) {
        var task = reportService.get(id);
        return R.ok(Map.of("taskId", id,
                "status", task.getStatus(),
                "progress", task.getProgress() == null ? 0 : task.getProgress(),
                "rowCount", task.getRowCount() == null ? 0 : task.getRowCount(),
                "errorMsg", task.getErrorMsg() == null ? "" : task.getErrorMsg()));
    }

    @GetMapping("/reports/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        Path path = reportService.file(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + path.getFileName() + "\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(new FileSystemResource(path));
    }
}

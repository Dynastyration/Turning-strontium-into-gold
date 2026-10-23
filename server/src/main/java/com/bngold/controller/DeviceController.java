package com.bngold.controller;

import com.bngold.common.PageResult;
import com.bngold.common.R;
import com.bngold.config.AuthContext;
import com.bngold.entity.Device;
import com.bngold.entity.DeviceCommand;
import com.bngold.service.DeviceService;
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
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public R<PageResult<Device>> page(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) Integer typeId,
                                      @RequestParam(required = false) Integer status,
                                      @RequestParam(required = false) Integer powerType,
                                      @RequestParam(required = false) Long projectId,
                                      @RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "20") long size) {
        return R.ok(deviceService.page(keyword, typeId, status, powerType, projectId, page, size));
    }

    @GetMapping("/geo")
    public R<List<Map<String, Object>>> geo(@RequestParam(required = false) Long projectId) {
        return R.ok(deviceService.geo(projectId));
    }

    @GetMapping("/{id}")
    public R<Device> detail(@PathVariable Long id) {
        return R.ok(deviceService.detail(id));
    }

    @PostMapping
    public R<Long> create(@RequestBody Device device) {
        return R.ok(deviceService.save(device));
    }

    @PutMapping("/{id}")
    public R<Void> update(@PathVariable Long id, @RequestBody Device device) {
        deviceService.update(id, device);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        deviceService.remove(id);
        return R.ok();
    }

    @GetMapping("/{id}/sensors")
    public R<Map<String, Object>> sensors(@PathVariable Long id) {
        return R.ok(deviceService.sensorsOf(id));
    }

    public record CommandReq(String cmdType, String payload) {
    }

    @PostMapping("/{id}/command")
    public R<Map<String, Object>> command(@PathVariable Long id, @RequestBody CommandReq req) {
        Long cid = deviceService.command(id, req.cmdType(), req.payload());
        return R.ok(Map.of("commandId", cid, "issuedBy", AuthContext.userId()));
    }

    @GetMapping("/{id}/command/{cid}")
    public R<DeviceCommand> commandStatus(@PathVariable Long id, @PathVariable Long cid) {
        return R.ok(deviceService.commandStatus(cid));
    }
}

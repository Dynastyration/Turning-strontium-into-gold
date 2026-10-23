package com.bngold.controller;

import com.bngold.common.R;
import com.bngold.config.AuthContext;
import com.bngold.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    public record LoginReq(String username, String password) {
    }

    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody LoginReq req) {
        return R.ok(authService.login(req.username(), req.password()));
    }

    @PostMapping("/refresh")
    public R<Map<String, Object>> refresh() {
        return R.ok(Map.of("accessToken", "", "expiresIn", 0));
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        return R.ok(authService.me(AuthContext.userId()));
    }

    @GetMapping("/menus")
    public R<java.util.List<Map<String, Object>>> menus() {
        java.util.List<Map<String, Object>> menus = new java.util.ArrayList<>();
        menus.add(Map.of("path", "/dashboard", "title", "数据大屏", "icon", "Odometer"));
        menus.add(Map.of("path", "/device", "title", "设备列表", "icon", "Cpu"));
        menus.add(Map.of("path", "/sensor", "title", "传感器管理", "icon", "Aim"));
        menus.add(Map.of("path", "/data", "title", "数据管理", "icon", "DataLine"));
        menus.add(Map.of("path", "/video", "title", "视频监控", "icon", "VideoCamera"));
        menus.add(Map.of("path", "/alarm", "title", "告警管理", "icon", "Bell"));
        return R.ok(menus);
    }
}

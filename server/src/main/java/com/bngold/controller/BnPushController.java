package com.bngold.controller;

import com.bngold.common.R;
import com.bngold.service.IngestService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * BN 云平台数据推送入口。不走 JWT，靠 IP 白名单 / X-Sign（此处略，见技术路线 4.1）。
 * 对端要求：POST + application/json，返回 {"code":0} 表示已落库。
 */
@Slf4j
@RestController
@RequestMapping("/open/bn")
@RequiredArgsConstructor
public class BnPushController {

    private final IngestService ingestService;
    private final ObjectMapper objectMapper;

    @PostMapping("/push")
    public R<Map<String, Object>> push(@RequestBody String body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            int accepted = ingestService.acceptPayload(node);
            return R.ok(Map.of("accepted", accepted));
        } catch (Exception e) {
            log.error("推送解析失败: {}", body.length() > 300 ? body.substring(0, 300) : body, e);
            return R.fail(40001, "报文解析失败");
        }
    }

    @GetMapping("/ping")
    public R<String> ping() {
        return R.ok("pong");
    }
}

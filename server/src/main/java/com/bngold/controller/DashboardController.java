package com.bngold.controller;

import com.bngold.common.R;
import com.bngold.service.AlarmService;
import com.bngold.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final AlarmService alarmService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "sse-push");
        t.setDaemon(true);
        return t;
    });

    @GetMapping("/dashboard/overview")
    public R<Map<String, Object>> overview(@RequestParam(required = false) Long projectId) {
        return R.ok(dashboardService.overview(projectId));
    }

    @GetMapping("/dashboard/trend")
    public R<List<Map<String, Object>>> trend(@RequestParam(defaultValue = "7") int days) {
        return R.ok(dashboardService.trend(days));
    }

    @GetMapping("/dashboard/top-alarm")
    public R<List<Map<String, Object>>> topAlarm(@RequestParam(defaultValue = "10") int limit) {
        return R.ok(dashboardService.topAlarm(limit));
    }

    @GetMapping("/dashboard/type-dist")
    public R<List<Map<String, Object>>> typeDist(@RequestParam(required = false) Long projectId) {
        return R.ok(dashboardService.typeDist(projectId));
    }

    @GetMapping("/dashboard/latest-readings")
    public R<List<Map<String, Object>>> latestReadings(@RequestParam(defaultValue = "8") int limit) {
        return R.ok(dashboardService.latestReadings(Math.min(Math.max(limit, 1), 50)));
    }

    @GetMapping("/alarms/unread-count")
    public R<Long> unread() {
        return R.ok(alarmService.unreadCount());
    }

    /** 大屏实时推送：10 秒一帧，服务端主动推，前端只渲染。 */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam(required = false) Long projectId) {
        SseEmitter emitter = new SseEmitter(0L);
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("overview")
                        .data(dashboardService.overview(projectId)));
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        }, 0, 10, TimeUnit.SECONDS);

        emitter.onCompletion(() -> task.cancel(true));
        emitter.onTimeout(() -> task.cancel(true));
        emitter.onError(e -> task.cancel(true));
        return emitter;
    }
}

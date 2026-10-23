package com.bngold.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bngold.common.R;
import com.bngold.entity.VideoChannel;
import com.bngold.mapper.VideoChannelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/videos")
@RequiredArgsConstructor
public class VideoController {

    private final VideoChannelMapper videoChannelMapper;

    @GetMapping
    public R<List<VideoChannel>> list(@RequestParam(required = false) Long projectId,
                                      @RequestParam(required = false) Integer online) {
        return R.ok(videoChannelMapper.selectList(new LambdaQueryWrapper<VideoChannel>()
                .eq(VideoChannel::getDeleted, 0)
                .eq(projectId != null, VideoChannel::getProjectId, projectId)
                .eq(online != null, VideoChannel::getOnline, online)
                .orderByAsc(VideoChannel::getSortNo)));
    }

    @GetMapping("/{id}/play")
    public R<Map<String, Object>> play(@PathVariable Long id) {
        VideoChannel c = videoChannelMapper.selectById(id);
        Map<String, Object> r = new LinkedHashMap<>();
        if (c == null) {
            r.put("playUrl", "");
            r.put("protocol", "flv");
            return R.ok(r);
        }
        r.put("playUrl", c.getPlayUrl());
        r.put("protocol", "flv");
        r.put("token", "demo-token-" + id);
        r.put("expiresIn", 300);
        r.put("online", c.getOnline());
        return R.ok(r);
    }

    @GetMapping("/{id}/snapshot")
    public R<Map<String, Object>> snapshot(@PathVariable Long id) {
        return R.ok(Map.of("url", ""));
    }
}

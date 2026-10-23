package com.bngold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_channel")
public class VideoChannel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;
    private Long projectId;
    private String name;
    private Integer protocol;
    private String gbDeviceId;
    private String channelNo;
    private String streamUrl;
    private String playUrl;
    private Integer online;
    private LocalDateTime lastHbAt;
    private Integer sortNo;
    private Integer deleted;
}

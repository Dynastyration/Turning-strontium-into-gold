package com.bngold.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("alarm_config")
public class AlarmConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private Integer targetType;
    private Long targetId;
    private Integer fieldId;
    private Integer ruleType;
    private Double threshold;
    private Double threshold2;
    private Integer durationSec;
    private Integer level;
    private Double hysteresis;
    private Integer silenceSec;
    private String notifyChannels;
    private Integer enabled;
    private LocalDateTime createdAt;
}
